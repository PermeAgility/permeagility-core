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

/* global angular */

var app = angular.module('permeagility', []);

// When an input has a directive text-build
app.directive('textBuild', ['$rootScope', function($rootScope) {
  return {
    link: function(scope, element, attrs) {
      $rootScope.$on('add', function(e, val) {
        var domElement = element[0];
        if (val == ' ') return;
        if (document.selection) {
          domElement.focus();
          var sel = document.selection.createRange();
          sel.text = val;
          domElement.focus();
        } else if (domElement.selectionStart || domElement.selectionStart === 0) {
          var startPos = domElement.selectionStart;
          var endPos = domElement.selectionEnd;
          var scrollTop = domElement.scrollTop;
          domElement.value = domElement.value.substring(0, startPos) + val + domElement.value.substring(endPos, domElement.value.length);
          domElement.focus();
          domElement.selectionStart = startPos + val.length;
          domElement.selectionEnd = startPos + val.length;
          domElement.scrollTop = scrollTop;
        } else {
          domElement.value += val;
          domElement.focus();
        }
      });
    }
  }
}]);

//Controller to help user build text from buttons and drop downs - mainly used for SQL/Script building
app.controller('TextBuildControl', function($scope, $rootScope) {
	  $scope.add = function(i) {
	    $rootScope.$broadcast('add', i);
	  }
	});

// Link Set Control
app.controller('LinkSetControl', function($scope) {
  $scope.values = null;
  $scope.selValue = null;
  $scope.toggleActive = function(s){    s.active = !s.active;        };
  $scope.resultList = function(){
    var result = "";
    angular.forEach($scope.values,
      function(v){
        if (v.active){
          if (result !== "") { result += "," }
          result += v.rid;
        }
      });
      return result;
  };
});

// Link List Control
app.controller('LinkListControl', function($scope) {
	$scope.values = null;
	$scope.listValues = null;
    $scope.selValue = null;

    $scope.delete = function(s) {
    	for (i = 0; i<$scope.listValues.length; i++) {
    		if ($scope.listValues[i].rid == s.rid && $scope.listValues[i].active == s.active) {
    			$scope.listValues[i] = null;
    			// Shift up and pop
    			for (j = i+1; j< $scope.listValues.length; j++) {
    				$scope.listValues[j-1] = $scope.listValues[j];
    			}
    			$scope.listValues.pop();
    		}
    	}
    }

    $scope.up = function(s) {
    	for (i = 1; i<$scope.listValues.length; i++) {
    		if ($scope.listValues[i].rid == s.rid &&
    			$scope.listValues[i].active == s.active) {
    			swap = angular.copy($scope.listValues[i]);
    			$scope.listValues[i] = $scope.listValues[i-1];
    			$scope.listValues[i-1] = swap;
    		}
    	}
    }

    $scope.down = function(s) {
    	for (i = 0; i<$scope.listValues.length-1; i++) {
    		if ($scope.listValues[i].rid == s.rid &&
    			$scope.listValues[i].active == s.active) {
    			swap = angular.copy($scope.listValues[i+1]);
    			$scope.listValues[i+1] = $scope.listValues[i];
    			$scope.listValues[i] = swap;
    			break;  // otherwise it goes to bottom
    		}
    	}
    }

    $scope.selected = function(s)   {
    	s.active++; // makes it unique
       	$scope.listValues[$scope.listValues.length] = angular.copy(s);
    };

    $scope.resultList = function() {
        var result = "";
        angular.forEach($scope.listValues,
                function(v){
                  if (result !== "") result += ",";
                  result += v.rid;
             });
        return result;
    };
});

// Link Map Control
app.controller('LinkMapControl', function($scope) {
	$scope.values = null;
	$scope.listValues = null;
    $scope.selValue = null;

    $scope.delete = function(s) {
    	for (i = 0; i<$scope.listValues.length; i++) {
    		if ($scope.listValues[i].rid == s.rid &&
    			$scope.listValues[i].active == s.active) {
    			$scope.listValues[i] = null;
    			// Shift up and pop
    			for (j = i+1; j< $scope.listValues.length; j++) {
    				$scope.listValues[j-1] = $scope.listValues[j];
    			}
    			$scope.listValues.pop();
    			// Enable list selection again
            	for (i = 0; i<$scope.values.length; i++) {
	        		if ($scope.values[i].rid == s.rid &&
    					$scope.values[i].active == s.active) {
    					$scope.values[i].active = 0;
    				}
    			}
    		}
    	}
    }

    $scope.up = function(s) {
    	for (i = 1; i<$scope.listValues.length; i++) {
    		if ($scope.listValues[i].rid == s.rid &&
    			$scope.listValues[i].active == s.active) {
    			swap = angular.copy($scope.listValues[i]);
    			$scope.listValues[i] = $scope.listValues[i-1];
    			$scope.listValues[i-1] = swap;
    			break;  // Stop searching
    		}
    	}
    }

    $scope.down = function(s) {
    	for (i = 0; i<$scope.listValues.length-1; i++) {
    		if ($scope.listValues[i].rid == s.rid &&
    			$scope.listValues[i].active == s.active) {
    			swap = angular.copy($scope.listValues[i+1]);
    			$scope.listValues[i+1] = $scope.listValues[i];
    			$scope.listValues[i] = swap;
    			break;  // otherwise it searches to bottom
    		}
    	}
    }

    $scope.selected = function(s)   {
        if (s.active > 0) {
        	alert("Already selected, no duplicates allowed");
        	} else {
            	s.active++; // makes it unique
	           	$scope.listValues[$scope.listValues.length] = angular.copy(s);
	        }
    };

    $scope.resultList = function() {
        var result = "";
        angular.forEach($scope.listValues,
                function(v){
                  if (result !== "") {
                   	result += ",";
                  }
                  result += "'"+v.map + "':"+ v.rid;
             });
        return result;
    };
});

/**
 * Filters out all duplicate items from an array by checking the specified key
 * @param [key] {string} the name of the attribute of each object to compare for uniqueness
 if the key is empty, the entire object will be compared
 if the key === false then no filtering will be performed
 * @return {array}
 */
angular.module('permeagility').filter('unique', function () {

  return function (items, filterOn) {

    if (filterOn === false) {
      return items;
    }

    if ((filterOn || angular.isUndefined(filterOn)) && angular.isArray(items)) {
      var hashCheck = {}, newItems = [];

      var extractValueToCompare = function (item) {
        if (angular.isObject(item) && angular.isString(filterOn)) {
          return item[filterOn];
        } else {
          return item;
        }
      };

      angular.forEach(items, function (item) {
        var valueToCheck, isDuplicate = false;

        for (var i = 0; i < newItems.length; i++) {
          if (angular.equals(extractValueToCompare(newItems[i]), extractValueToCompare(item))) {
            isDuplicate = true;
            break;
          }
        }
        if (!isDuplicate) {
          newItems.push(item);
        }

      });
      items = newItems;
    }
    return items;
  };
});
