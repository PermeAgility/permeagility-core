/* 
 * Copyright 2015 PermeAgility Incorporated.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Foundational stuff
d3.selection.prototype.first = function() {
  return d3.select(this[0][0]);
};
d3.selection.prototype.last = function() {
  var last = this.size() - 1;
  return d3.select(this[0][last]);
};

// Database model and data viewer
var TOP_MARGIN = 50;
var h = $("#service").height() - TOP_MARGIN;
var w = $("#service").width();
d3.select("#service").style("cursor", "default");
var svg = d3.select("#service").attr("visuilityBuild",true).append("svg").attr("width", w).attr("height", h);

// Tooltip
var tooltip = d3.select('body')
    .append('div')
    .style('position', 'absolute')
    .style('bottom', h/3*2 + 'px')
    .style('left', w/2 - w/10 +'px')
    .style('right', w/2 - w/10 +'px')
    .style('z-index', '10')
    .style('visibility', 'hidden')
    .style('background', 'steelblue')
    .style('border-radius','8px')
    .style('padding','8px')
    .style('opacity',0)
    .style('pointer-events','none')
    .text('a simple tooltip');

tooltip.show = function(text,duration) {
        tooltip.style('bottom', h/3*2 + 'px')
            .style('left', w/2 - w/10 +'px')
            .style('right', w/2 - w/10 +'px')
            .text(text);
        tooltip.transition("alertShow").duration(100).style('visibility','visible').style('opacity', 0.9);
        tooltip.transition("alertFade").duration(duration).delay(500).style("opacity",0);
};

tooltip.showAt = function(text,x,y) {
        tooltip.style('left', x +'px')
            .style('top', y + 'px')
            .style('width', 250 +'px')
            .style('height', 50 + 'px')
            .text(text);
            tooltip.transition("tipShow").duration(100).style('visibility','visible').style('opacity', 0.75);
            //tooltip.transition("tipFade").duration(500).delay(3000).style("opacity",0);
};

tooltip.hide = function() {
    tooltip.style("visibility","hidden");
};

// Underlay to capture click and drag on the diagram background
svg.append("rect")
   .attr({"class": "overlay" , "width": w , "height": h, "opacity": 0.0 })
   .on( "mousedown", selectRectStart)
   .on( "mousemove", selectRectMouseMove)
   .on( "mouseup", selectRectEnd);

// Arrow line marker definition
svg.append("defs").selectAll("marker")
    .data(["triangle"])      // Different link/path types can be defined here
  .enter().append("marker")    // This section adds in the arrows
    .attr("id", function (d) {   return d; })
    .attr("viewBox", "0 -5 10 10")
    .attr("refX", 40)
    .attr("refY", 0)
    .attr("markerWidth", 10)
    .attr("markerHeight", 10)
    .attr('markerUnits', 'userSpaceOnUse')
    .attr("orient", "auto")
    .attr("class", "link")
  .append("svg:path")
    .attr("d", "M0,-5L10,0L0,5");

// Text drop shadow filter
var defs = svg.append("defs");
var filter = defs.append("filter")
    .attr("id", "drop-shadow")
    .attr("height", "130%"); 
filter.append("feGaussianBlur")
    .attr("in", "SourceAlpha")
    .attr("stdDeviation", 3)
    .attr("result", "blur");
filter.append("feOffset")
    .attr("in", "blur")
    .attr("dx", 4)
    .attr("dy", 4)
    .attr("result", "offsetBlur");
var feMerge = filter.append("feMerge");
feMerge.append("feMergeNode")
    .attr("in", "offsetBlur")
feMerge.append("feMergeNode")
    .attr("in", "SourceGraphic");

// Link and node data
var links = [], nodes = [];

var LINK_SIZE = 25;

// This will come from outside later but hardcoded for now
var nodeTypes = { "control": { "width":20, "maxheight":15, "minheight":15, "corner": 20, "color": "#ccc", "controls":"", "offset":"topleft" } 
                 ,"table": { "width":80, "maxheight":18, "minheight":5, "corner": 0, "color": "cyan", "controls":"x,r,c" }
                 ,"row": { "width":30, "maxheight":13, "minheight":5, "corner": 10, "color": "yellow", "controls":"x,t,d", "offset":"bottom" }
                 ,"column": { "width":90, "maxheight":13, "minheight":5, "corner": 5, "color": "orange", "controls":"x", "offset":"topright" }
                 ,"data": { "width":90, "maxheight":13, "minheight":5, "corner": 15, "color": "lightgreen", "controls":"x", "offset":"right" }
                };

// Download the given text to a file directly from the browser (not supported in all)
function download(filename, text) {
    var pom = document.createElement('a');
    pom.setAttribute('href', 'data:image/svg;charset=utf-8,' + encodeURIComponent(text));
    pom.setAttribute('download', filename);
    if (document.createEvent) {
        var event = document.createEvent('MouseEvents');
        event.initEvent('click', true, true);
        pom.dispatchEvent(event);
    } else {
        pom.click();
    }
}

d3.select("#save_as_svg").on("click", function(e) {
  	var svgx = document.getElementById("service").getElementsByTagName("svg")[0];
	download("view.svg",(new XMLSerializer).serializeToString(svgx));
});

// This attaches the main selector to the graph
d3.select("#tableSelector").on("change", function(e) {
	if (this.value) {
	  	getMore("TABLE",this.options[this.selectedIndex].label);
		d3.select("#tableSelector").node().blur();
	}
});

// Rectangle selection functions
function selectRectMouseMove() {
    var s = svg.select( "rect.selection");
    if( !s.empty()) {
        var p = d3.mouse( this);
        var r = { x: parseInt( s.attr( "x"), 10), y: parseInt( s.attr( "y"), 10),
                width: parseInt( s.attr( "width"), 10), height: parseInt( s.attr( "height"), 10)};
        var move = { x : p[0] - r.x, y : p[1] - r.y };
        if( move.x < 1 || (move.x*2<r.width)) {
            r.x = p[0];
            r.width -= move.x;
        } else {
            r.width = move.x;       
        }
        if( move.y < 1 || (move.y*2<r.height)) {
            r.y = p[1];
            r.height -= move.y;
        } else {
            r.height = move.y;       
        }
        s.attr(r);
        //var prevSelectedNodes = d3.selectAll(".selected");
        nodeSVG.each( function( d, i) {
        	var e = d3.select(this).selectAll("rect");
        	var dw = e.attr("width");
        	var dh = e.attr("height");
            //if ((d.x+d.width/2>=r.x || d.x-d.width/2<=r.x+r.width )  // Should only check if touched by rectangle (not working)
             //  && (d.y+d.height/2>=r.y || d.y-d.height/2<=r.y+r.height)) {
              if (d.x-dw/2>=r.x && d.x+dw/2<=r.x+r.width 
                 && d.y-dh/2>=r.y && d.y+dh/2<=r.y+r.height) {
                  d3.select(this).classed("selected", true); 
              } else {
                  d3.select(this).classed("selected", false); 
              }
        //  }
        } );
    }
}

function selectRectEnd() {
  svg.selectAll( "rect.selection").remove();
  var selectedNodes = d3.selectAll(".selected");
  if (selectedNodes[0].length > 0) {
  	selectedNodes.last().each(function(d) { updateControls(d); } );
  } else {
    removeControls();
  }
}

function selectRectStart() {
   if (!d3.event.shiftKey) {
     var selectedNodes = d3.selectAll(".selected");
     selectedNodes.classed("selected", false);
   }
  var p = d3.mouse( this);
  svg.append( "rect")
    .attr({rx: 6, ry: 6, class: "selection", x: p[0], y: p[1], width: 0, height: 0, opacity: 0.5 })
    .on("mousemove", selectRectMouseMove)
    .on("mouseup", selectRectEnd);  
}

function createFilter() {
  d3.select("#service")
    .append("div")
    	.attr("id","sidebar")
    	.style({ display:"block", position:"fixed", bottom:"0px", right:"0px", background:"#999", opacity: 0.5, padding: "5px" })
    	.selectAll("div")
    .data(["table", "row", "column", "data"])
    .enter()  // For each incoming element of data
//      .append("div").attr("class", "checkbox-container")  // Makes them stack
      .append("label")
      .each(function (d) {
        d3.select(this).append("input")
          .attr("type", "checkbox")
          .attr("id", function (d) { return "chk_" + d; })
          .attr("checked", true)
          .on("click", function (d, i) {
            var lVisibility = this.checked ? "visible" : "hidden";
            filterGraph(d, lVisibility);
          });
        d3.select(this).append("span").text(function (d) { return d; });
      });
}

function filterGraph(aType, aVisibility) {
  nodeSVG.style("visibility", function (d) {
    var lOriginalVisibility = $(this).css("visibility");
    return d.type === aType ? aVisibility : lOriginalVisibility;
  });
  updateLinkVisibility();
}

function updateLinkVisibility() {
  linkSVG.style("visibility", function (o, i) {
    var s,t;
    nodeSVG.each(function(d,i) {
      if (o.source === d) s = d3.select(this).style("visibility");
      if (o.target === d) t = d3.select(this).style("visibility");
    });
    if (s === "visible" && t === "visible") return "visible"; else return "hidden";
  });  
}

function dragstart(d) {
  svg.selectAll("rect.selection").remove();
  d3.select(this).classed('fixed', d.fixed = true);
}

function dragmove(d, i) {
    // If the node is positioned offset to parent, push the drag to the parent
    while (d.parent) { d = d.parent; }  // Go to the top parent
    d.px += d3.event.dx;
    d.py += d3.event.dy;
    d.x += d3.event.dx;
    d.y += d3.event.dy; 
    tick(); // this is the key to make it work together with updating both px,py,x,y on d !
}

function dragend(d) {
  d3.select(this).transition().delay(1000).each(function(d) { d.fixed = false; });
}

function dblclickNode(d) {
    if (d.type === "table") {
        window.open("permeagility.web.Table?TABLENAME="+d.name, '_blank');
    } else if (d.type === "row") {
        window.open("permeagility.web.Table?EDIT_ID="+d.id.split(".")[1], '_blank');
    }
}

// Send keystroke as char to each selected node
d3.select("body").on("keydown", function() {
     var key = String.fromCharCode(d3.event.keyCode);
     if (key !== "") {
        // Pop text
        svg.append("text").attr("x","5").attr("y","30").style("font-size","20px")
            .text("Doing: " + String.fromCharCode(d3.event.keyCode) )  
            .transition().duration(2000).style("font-size","5px").style("fill-opacity",".1").remove();
        // Execute the action on the selected nodes
        d3.selectAll(".selected").each( function (d,i) {
  		  executeControl(d, key);
        } );
     }
});

function clickNode(d) {
    if (d3.event.defaultPrevented) return; // ignore drag
    
    var detail = d.id.substr(d.id.indexOf(".")+1);
    var selectedNodes = d3.selectAll(".selected");
    
    if (d.type === "control") {
        d3.selectAll(".selected").each( function (d,i) {
  		  executeControl(d, detail);
        } );
   } else {  // We clicked a non-control node - Handle node selection
      if (d3.event.shiftKey) {  // Multi select with shift
          d3.select(this).classed("selected", !d3.select(this).classed("selected"));
      } else {
          selectedNodes.classed("selected", false);
          d3.select(this).classed("selected", true);
      }
      updateControls(d);
        if (!d3.event.shiftKey) {
            getMore(d.type,d.id.substr(d.id.indexOf(".")+1));
        }
   }
}

function executeControl(d, detail) {
      var delCount = 0;
      var srcId = d.id; 
      while (srcId.split(".")[0] === "control") {
        srcId = findParent(srcId);
      }
      detail = detail.toLowerCase().substring(0,1);
      if (detail === "r" && d.type === "table") {  // Roll up the rows
        var lastNode;
        while(r = findChild(srcId,"row")) {
          if (lastNode) delCount += removeNode(lastNode);
          lastNode = r;
          srcId = r;
        }
        if (lastNode) delCount += removeNode(lastNode);
        if (delCount === 0) {  // There were none, so get some
	      getMore("TABLE",srcId.split(".")[1],detail);
        }
      } else if (detail === "c" && d.type === "table") {  // Roll up the columns
        var lastNode;
        while(r = findChild(srcId,"column")) {
          if (lastNode) delCount += removeNode(lastNode);
          lastNode = r;
          srcId = r;
        }
        if (lastNode) delCount += removeNode(lastNode);
        if (delCount === 0) {  // There were none, so get some
	      getMore("TABLE",srcId.split(".")[1],detail);
        }
      } else if (detail === "d" && d.type === "row") {  // Roll up the data
        var lastNode;
        while(r = findChild(srcId,"data")) {
          if (lastNode) delCount += removeNode(lastNode);
          lastNode = r;
          srcId = r;
        }
        if (lastNode) delCount += removeNode(lastNode);
        if (delCount === 0) {  // There were none, so get some
	      getMore("ROW",srcId.split(".")[1],detail);
        }
      } else if (detail === "t" && d.type === "row") {  // Get table
	      getMore("ROW",srcId.split(".")[1],detail);
      } else if (detail === "x") {  // Remove the node
            removeControls();
            removeNode(srcId);
      } else if (detail === "x*") {  // Remove the nodes in a multiselect
            removeControls();
            selectedNodes.each( function(d) { removeNode(d.id); } );
      } else {
		  //alert("Control pressed\nid="+srcId+" detail="+detail);
      }  
}

function updateControls(d) {  // Using controls attribute in nodeTypes structure
  removeControls();
  var selectedNodes = d3.selectAll(".selected");
  var controls = nodeTypes[d.type].controls.split(",");
  var target = d.id;
  var newLinks = [];
  for (var i=0, len = controls.length; i < len; i++) {
  	var newSourceId = "control."+controls[i]+(selectedNodes[0].length > 1 ? "*" : "");
  	newLinks.push({targetId: target, sourceId: newSourceId, chain: true });
  	target = newSourceId;
  }
  if (newLinks.length > 0) handleData(newLinks);
  update();
}

function findParent(id) {
  var i = 0;
  while(i<links.length) {
    if (links[i].sourceId === id) {
      return links[i].targetId;
    } else i++;
  }          
}
  
// Find source [of type] where target = id
function findChild(id, type) {
   var i = 0;
   while(i<links.length) {
    if (links[i].targetId === id) {
      if (type) {
        if (links[i].source.type === type) {
          return links[i].sourceId;
        }
      } else {
        return links[i].sourceId;
      }
    }
    i++;
  } 
}
  
function removeControls() {
  // Remove all control links
  var i = 0;
  while(i<links.length) {
    if (links[i].source.type === "control") {
      links.splice(i,1);
    } else i++;
  }
  // Remove all control nodes
  var i = 0;
  while(i<nodes.length) {
    if (nodes[i].type === "control") {
      nodes.splice(i,1);
    } else i++;
  }
  update();  // Clear removed stuff
}
  
function releaseChain(node) {  // Free a chain of offset nodes
    var n = node;
    var i = 0;
    while(i<links.length) {
        if (links[i].targetId === n.id && links[i].source.parent === n) {
            links[i].source.parent = null;
            n = links[i].source;  // new node
            i = 0;  // start again
        } else i++;
    }    
}

function removeNode(id) {
  //alert("removing node "+id);
  var delCount = 0;
  // Remove all links
  var i = 0;
  while(i<links.length) {
    if (links[i].targetId === id || links[i].sourceId === id) {
      var s = links[i].source;
      var t = links[i].target;
      t.parent = null;
      links.splice(i,1);
      if (s.type !== t.type) releaseChain(s);
      delCount++;
    } else i++;
  }
  // Remove all control nodes
  var i = 0;
  while(i<nodes.length) {
    if (nodes[i].id === id) {
      nodes.splice(i,1);
      delCount++;
    } else i++;
  }
  update();  // Clear removed stuff
  return delCount;
}

function getMore(type,key,detail) {
   //tooltip.show("Getting more",1000);  // for testing the tooltip
   if (detail) detail="&DETAIL="+detail; else detail="";
   var id = type.toLowerCase()+"."+key;
   var selectedNodes = d3.selectAll(".selected");
   d3.json("/permeagility.web.VisuilityData?TYPE="+type.toUpperCase()+"&ID="+key+detail, function(data) {
	  if (data && (data.links || data.nodes)) {
	    handleData(data.links, data.nodes);
	    update();
	  } else {
		  tooltip.show("no data: "+(data ? data.error : "No Error"),1000);
	  	return;
	  }
	  // Show that we have completed the retrieval of all related nodes for this node by growing to full height
      nodeSVG.each( function( d, i) {
        if (d.id === id) {
           d3.select(this).select("rect").transition().duration(900)
                .attr("width", function(d) { return d.textWidth ? d.textWidth+10 : nodeTypes[d.type].width; })
                    .attr("height", function(d) { return nodeTypes[d.type].maxheight; });
                  if (selectedNodes[0].length === 0) {
                    d3.select(this).classed("selected",true);
                    updateControls(d);
                  }
            }
   });
  });
}

function handleData(newlinks, newnodes) {
    var newNodeMap = newnodes ? newnodes.map(function(d) { return d.id; }) : undefined;
    var nodeMap = nodes ? nodes.map(function(d) { return d.id; }) : undefined;
    if (newnodes) {  // Single unlinked nodes should show as well
//        var nodeMap = nodes ? nodes.map(function(d) { return d.id; }) : undefined;
        for (var i = 0, c = newnodes.length; i<c; i++) {
            var ni = nodeMap ? nodeMap.indexOf(newnodes[i].id) : -1;
            if (ni === -1) {
                addNode(newnodes[i], null);
            }
        }
    }
    nodeMap = nodes ? nodes.map(function(d) { return d.id; }) : undefined; // Update the node map
    if (newlinks) {
        for (var i = 0, c = newlinks.length; i<c; i++) {
            var sourceNode = {id: newlinks[i].sourceId};
            var targetNode = {id: newlinks[i].targetId};
            var si = nodeMap ? nodeMap.indexOf(sourceNode.id) : -1;
            var ti = nodeMap ? nodeMap.indexOf(targetNode.id) : -1;
            var nsi = newNodeMap ? newNodeMap.indexOf(sourceNode.id) : -1;
            var nti = newNodeMap ? newNodeMap.indexOf(targetNode.id) : -1;
            if (nsi > -1) sourceNode = newnodes[nsi];
            if (nti > -1) targetNode = newnodes[nti];
            targetNode = addNode(targetNode, null);
            sourceNode = addNode(sourceNode, targetNode);
            var dist = newlinks[i].distance;
            if (!dist) dist = 1;
            addLink({source: sourceNode, target: targetNode
             , sourceId: newlinks[i].sourceId, targetId: newlinks[i].targetId
             , distance: dist, chain: newlinks[i].chain});
	}
    }
}

// Checks whether node already exists in nodes or not
function addNode(node, otherNode) {
    var i = nodes.map(function(d) { return d.id; }).indexOf(node.id);
    if (i === -1) {
        node.type = node.id.split(".")[0];
//        if (otherNode && nodeTypes[node.type].offset) node.parent = otherNode;                        
        if (nodes.length === 0) {  // first one goes in center
            node.x = w/2; node.px = node.x;
            node.y = h/2; node.py = node.y;
        }
        nodes.push(node);
        return node;
    } else {
        //nodes[i] = node;  // Replace the node doesnt work unless change the id
        // update the node??? // TODO: update the node data
//        if (otherNode && nodeTypes[nodes[i].type].offset && !nodes[i].parent) {
//            nodes[i].parent = otherNode;
//        }
        return nodes[i];
    }
}

// Checks whether link already exists in links or not
function addLink(link) {
    var linkMap = links.map(function(d) { return d.source.id+"-"+d.target.id; });
    if (linkMap.indexOf(link.source.id+"-"+link.target.id) === -1
     && linkMap.indexOf(link.target.id+"-"+link.source.id) === -1) {
         if (!link.source.parent && nodeTypes[link.source.type].offset && link.chain) link.source.parent = link.target;                        
        //console.log("added link "+link.sourceId+"-"+link.targetId);
        links.push(link);
    }
}

function tick() {
    // Update relative positioned objects
    nodeSVG.selectAll("rect")
        .each(function(d) {
            var offset = nodeTypes[d.type].offset;
            if (offset === "top" && d.parent) {
                d.x = d.parent.x;
                d.y = d.parent.y - nodeTypes[d.type].maxheight/2 - nodeTypes[d.parent.type].maxheight/2;
            }
            if (offset === "topleft" && d.parent) {
                //d.x = d.parent.x - d.parent.textWidth/2 - nodeTypes[d.type].width/2;
                //d.y = d.parent.y - nodeTypes[d.type].maxheight;
                if (d.parent.type === d.type) {
                    d.x = d.parent.x - nodeTypes[d.type].maxheight;
                    d.y = d.parent.y - 10;
                } else {
                    d.x = d.parent.x - d.parent.textWidth/2.0;
                    d.y = d.parent.y - 15;
                }
            }
            if (offset === "bottom" && d.parent) {
                d.x = d.parent.x;
                d.y = d.parent.y + nodeTypes[d.parent.type].maxheight;
            }
            if (offset === "topright" && d.parent) {
                if (d.parent.type === d.type) {
                    d.x = d.parent.x + nodeTypes[d.type].maxheight;
                    d.y = d.parent.y - 10;
                } else {
                    d.x = d.parent.x + d.parent.textWidth/2.0;
                    d.y = d.parent.y - 15;
                }
            }
            if (offset === "right" && d.parent) {
                d.x = d.parent.x + d.parent.textWidth/2 + d.textWidth/2 + 5;
                d.y = d.parent.y;
            }
            if (offset === "left" && d.parent) {
                d.x = d.parent.x - d.parent.textWidth/2 - d.textWidth/2 - 5;
                d.y = d.parent.y;
            }        
        })
        .attr("x", function(d) { 
            var cw = d3.select(this).attr("width")/2;  // center widthwise
            return Math.max(-cw, Math.min(w - cw, d.x - cw));  // Keep rect in view
        })
        .attr("y", function(d) {
            var ch = d3.select(this).attr("height")/2;  // center heightwise
            return Math.max(-ch, Math.min(h - ch, d.y - ch));  // Keep rect in view
        })
        .attr("transform", function(d) {
            if (nodeTypes[d.type].offset === "topleft") {
                return "rotate(-30 "+d.x+" "+d.y+")";
            } else if (nodeTypes[d.type].offset === "topright") {
                return "rotate(30 "+d.x+" "+d.y+")";
            } else {
                return;
            }
        });

    linkSVG.attr("x1", function(d) {return d.source.x;}).attr("y1", function(d) {return d.source.y;})
            .attr("x2", function(d) {return d.target.x;}).attr("y2", function(d) {return d.target.y;});

    nodeSVG.selectAll("text").attr("transform", function(d) {
        if (nodeTypes[d.type].offset === "topleft") {
            return "translate("+d.x+","+d.y+")rotate(-30 "+0+" "+0+")"; 
        } else if (nodeTypes[d.type].offset === "topright") {
            return "translate("+d.x+","+d.y+")rotate(30 "+0+" "+0+")"; 
        } else {
            return "translate("+d.x+","+d.y+")";             
        }
        
    });
}
          
function splitLines(text) {
  text.each(function(d) {
    var text = d3.select(this),
        lines = text.text().split(/\n+/).reverse(),
        aline = "", lineNumber = 0, lineHeight = 10, // px
        tspan = text.text(null),
        maxW = 1;
    while (aline = lines.pop()) {
        tspan = text.append("tspan").attr("x", 0)
          .attr("dy", (lineHeight * lineNumber++ + 5) + "px")
          .text(aline).style("user-select", "none");
    	var tlen = tspan.node().getComputedTextLength();
        if (tlen > maxW) maxW = tlen;
    }
    d.textWidth = maxW;
  });
}

// Setup service resizing and updating the force diagram
function resize(e){
    w = $("#service").width(); 
    h = $("#service").height()-TOP_MARGIN; 
    svg.attr("width", w);
    svg.attr("height", h);
    force.size([w, h]).resume();
}
d3.select(window).on("resize", resize);

svg.append("g").attr("class", "links");
svg.append("g").attr("class", "nodes");

var linkSVG = svg.select(".links").selectAll(".link"),
	nodeSVG = svg.select(".nodes").selectAll("g");

var force = d3.layout.force()
    .nodes(nodes)
    .links(links)
    .size([w, h - TOP_MARGIN])
    .linkDistance(function(d) { 
       // if (d.source.type === "table") return 150;
       // if (d.target.type === "control") return 5;
       // if (d.source.type === "control") return 25;
       // if (d.source.type === "column") return 20;
       // if (d.source.type === "row") return 100;
       // if (d.target.type === "row") return 100;
       // if (d.source.type === "data") return 20;
        return w/4;
    })
    .linkStrength(0.01)
    .charge(function(d,i) {
        if (nodeTypes[d.type].offset) {  // relative positioned objects have very little charge
            return -10;
        } else {
            return -150;
        }
    })
    .gravity(0.01)
    .friction(0.01)
    .on("tick", tick);

function update() {
    // enter, update and exit
    force.start();
  
    linkSVG = linkSVG.data(force.links(), function(d) { return d.source.id+"-"+d.target.id; });
    linkSVG.enter()  
      .append("line")
      .filter( function(d) { if (d.source.type === "control" || d.chain) return false; else return true; })
      .attr("class", "link")
      .attr("stroke-width", 2)
      .attr("marker-end", "url(#triangle)");
    linkSVG.exit().remove();

    nodeSVG = nodeSVG.data(force.nodes(), function(d) { return d.id; });

    var nodeGroup = nodeSVG.enter().append("g");  // Must create group for rect and text

    nodeGroup.append("rect")
        .attr("rx", function(d) { return nodeTypes[d.type].corner; })
        .attr("ry", function(d) { return nodeTypes[d.type].corner; })
        .attr("class", "node")
        .attr("width", 1)
        .attr("height", 1)
        .attr("stroke-width", 2)
        .attr("fill", function(d) { return nodeTypes[d.type].color; });

    nodeGroup.append("text")
        .attr("class", "nodeTitle")
        .attr("text-anchor", "middle")
        .attr("opacity", 0)
        .text(function (d) { 
            var i=d.id.indexOf("."); 
            if (d.type === "control") {
                return d.id.substr(i+1,20+i);
            } else {
	      	return d.name ? d.name.substring(0,20) : d.id.substr(0,i)+"\n"+d.id.substr(i+1,20+i); 
            }
    	})
        .call(splitLines)
    	.transition().duration(1000)
    	    .attr("opacity", 1);
             
  nodeGroup.on('click', clickNode)
        .on('dblclick', dblclickNode)
        .on("mouseup", function(d) { 
            if (d3.event.defaultPrevented) return; // ignore drag
            selectRectEnd(); 
        })
        .on("mousemove", function(d) { 
            if (d3.event.defaultPrevented) return; // ignore drag
            selectRectMouseMove(); 
        })
        .on("mouseover", function(d) { if (d.description) tooltip.showAt(d.description, d3.event.x+15, d3.event.y+10); })
        .on("mouseout", function(d) { if (d.description) tooltip.hide(); })
        .call(force.drag().on('dragstart', dragstart).on('drag',dragmove).on('dragend', dragend));

  nodeGroup.selectAll("rect")
        .transition().duration(900)
	   .attr("width", function(d) { return d.textWidth ? d.textWidth+10 : nodeTypes[d.type].width; })
      	   .attr("height", function(d) { return nodeTypes[d.type].minheight; });
   
   nodeSVG.exit().select("rect").transition().duration(500)
   		.attr("opacity",0).attr("width",0).attr("height",0);	
   nodeSVG.exit().select("text").transition().duration(500).attr("opacity",0);	
   nodeSVG.exit().transition().duration(500).remove();	
  
   updateLinkVisibility();
}

createFilter();
//Do this to get Visuility to load data
//getMore("TABLE","menu");

