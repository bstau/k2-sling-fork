/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.scripting;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.apache.sling.scripting.javascript.RhinoJavaScriptEngineFactory;

/** Helpers to run javascript code fragments in tests */
public class ScriptEngineHelper {
    private static ScriptEngine engine;

    public static class Data extends HashMap<String, Object> {
    }
    
    private static ScriptEngine getEngine() {
        if(engine == null) {
            synchronized (ScriptEngineHelper.class) {
                engine = new RhinoJavaScriptEngineFactory().getScriptEngine(); 
            }
        }
        return engine;
    }
    
    public String evalToString(String javascriptCode) throws ScriptException {
        return evalToString(javascriptCode, null);
    }
    
    public String evalToString(String javascriptCode, Map<String, Object> data) throws ScriptException {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        ScriptContext ctx = new SimpleScriptContext();
        
        final Bindings b = new SimpleBindings();
        b.put("out", pw);
        if(data != null) {
            for(Map.Entry<String, Object> e : data.entrySet()) {
                b.put(e.getKey(), e.getValue());
            }
        }
        
        ctx.setBindings(b, ScriptContext.ENGINE_SCOPE);
        ctx.setWriter(sw);
        ctx.setErrorWriter(new OutputStreamWriter(System.err));
        getEngine().eval(javascriptCode, ctx);
        return sw.toString();
    }
}