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
package org.apache.sling.servlet.resolver.defaults;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.BeanMap;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

/**
 * The <code>DefaultServlet</code> is a very simple default resource handler.
 * <p>
 * The default servlet is not registered to handle any concrete resource
 * type. Rather it is used internally on demand.
 *
 */
public class DefaultServlet extends SlingSafeMethodsServlet {

    @Override
    protected void doGet(SlingHttpServletRequest request,
            SlingHttpServletResponse response) throws IOException {

        Resource resource = request.getResource();

        // cannot handle the request for missing resources
        if (resource instanceof NonExistingResource) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // format response according to extension (use Mime mapping instead)
        String extension = request.getRequestPathInfo().getExtension();
        if ("html".equals(extension) || "htm".equals(extension)) {
            this.renderContentHtml(resource, response);
        } else if ("xml".equals(extension)) {
            this.renderContentXML(resource, response);
        } else if ("properties".equals(extension)) {
            this.renderContentProperties(resource, response);
        } else if ("json".equals(extension)) {
            this.renderContentJson(resource, response);
        } else {
            // default rendering as plain text
            this.renderContentText(resource, response);
        }
    }

    private void renderContentHtml(Resource resource,
            SlingHttpServletResponse response) throws IOException {
        Map<Object, Object> contentMap = new TreeMap<Object, Object>(
            this.asMap(resource));

        response.setContentType("text/html; charset=UTF-8");
        PrintWriter pw = response.getWriter();

        pw.println("<html><head><title>");
        pw.println(resource.getURI());
        pw.println("</title></head><body bgcolor='white' fgcolor='black'>");
        pw.println("<h1>Contents of <code>" + resource.getURI()
            + "</code></h1>");

        pw.println("<table>");
        pw.println("<tr><th>name</th><th>Value</th></tr>");

        for (Map.Entry<Object, Object> entry : contentMap.entrySet()) {
            pw.println("<tr><td>" + entry.getKey() + "</td><td>"
                + entry.getValue() + "</td></tr>");
        }

        pw.println("</body></html>");
    }

    private void renderContentText(Resource resource,
            SlingHttpServletResponse response) throws IOException {
        Map<Object, Object> contentMap = new TreeMap<Object, Object>(
            this.asMap(resource));

        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter pw = response.getWriter();

        pw.println("Contents of " + resource.getURI());
        pw.println();

        for (Map.Entry<Object, Object> entry : contentMap.entrySet()) {
            pw.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    private void renderContentProperties(Resource resource,
            SlingHttpServletResponse response) throws IOException {
        Properties props = new Properties();

        Map<Object, Object> contentMap = new TreeMap<Object, Object>(
            this.asMap(resource));
        for (Map.Entry<Object, Object> entry : contentMap.entrySet()) {
            props.setProperty(String.valueOf(entry.getKey()),
                String.valueOf(entry.getValue()));
        }

        response.setContentType("text/plain; charset=ISO-8859-1");

        OutputStream out = response.getOutputStream();
        props.store(out, "Contents of " + resource.getURI());
    }

    private void renderContentXML(Resource resource,
            SlingHttpServletResponse response) throws IOException {
        Map<Object, Object> contentMap = new TreeMap<Object, Object>(
            this.asMap(resource));

        response.setContentType("text/xml; charset=UTF-8");
        PrintWriter pw = response.getWriter();

        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        pw.println("<content>");

        for (Map.Entry<Object, Object> entry : contentMap.entrySet()) {

            pw.println("  <property>");
            pw.println("    <name>" + entry.getKey() + "</name>");

            if (entry.getValue() instanceof Collection) {
                pw.println("    <values>");
                Collection<?> coll = (Collection<?>) entry.getValue();
                for (Iterator<?> ci = coll.iterator(); ci.hasNext();) {
                    pw.println("      <value>" + ci.next() + "</value>");
                }
                pw.println("    </values>");

            } else {
                pw.println("    <value>" + entry.getValue() + "</value>");
            }
            pw.println("  </property>");
        }

        pw.println("</content>");
    }

    private void renderContentJson(Resource resource,
            SlingHttpServletResponse response) throws IOException {

        Map<Object, Object> contentMap = new TreeMap<Object, Object>(
            this.asMap(resource));

        response.setContentType("text/x-json; charset=UTF-8");
        PrintWriter pw = response.getWriter();

        pw.println("{");

        boolean notFirst = false;
        for (Map.Entry<Object, Object> entry : contentMap.entrySet()) {

            if (notFirst) {
                pw.println(',');
            } else {
                notFirst = true;
            }

            pw.print("  \"" + entry.getKey() + "\": ");

            if (entry.getValue() instanceof Collection) {
                pw.println("[");
                Collection<?> coll = (Collection<?>) entry.getValue();
                for (Iterator<?> ci = coll.iterator(); ci.hasNext();) {
                    pw.print("    ");
                    this.printObjectJson(pw, ci.next());
                    if (ci.hasNext()) {
                        pw.println(',');
                    }
                }
                pw.println();
                pw.print("  ]");

            } else {
                this.printObjectJson(pw, entry.getValue());
            }
        }

        pw.println();
        pw.println("}");

    }

    private void printObjectJson(PrintWriter pw, Object object) {
        boolean quote = !((object instanceof Boolean) || (object instanceof Number));
        if (quote) {
            pw.print('"');
        }
        pw.print(object);
        if (quote) {
            pw.print('"');
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> asMap(Resource resource) {
        
        Object object = resource.adaptTo(Object.class);
        if (object != null) {
            if (object instanceof Map) {
                return (Map<Object, Object>) object; // unchecked cast
            }

            return new BeanMap(object); // unchecked cast
        }

        // no objects available
        return null;
    }
}