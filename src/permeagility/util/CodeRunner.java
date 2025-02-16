package permeagility.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.jactl.Jactl;
import io.jactl.JactlContext;
import io.jactl.JactlScript;

public class CodeRunner {
    private static ConcurrentHashMap<String, JactlScript> scripts = new ConcurrentHashMap<>();
    private static JactlContext context = null;


    public static String runCode(String code, Map<String,Object> globals) {
        if (context == null) {
            Jactl.function().name("query")
            .param("dbcon")
            .param("expression")
            .impl(DatabaseConnection.class,"query")
            .register();
            Jactl.function().name("update")
            .param("dbcon")
            .param("expression")
            .impl(DatabaseConnection.class,"update")
            .register();
            context = JactlContext.create().build();
                
        }
        JactlScript script = scripts.get(code);
        if (script == null) {
            System.out.println("Compiling code: "+code);
            script = Jactl.compileScript(code, globals, context);
            scripts.put(code, script);
        }
        Object result = script.runSync(globals);
        return result.toString();
    }
}
