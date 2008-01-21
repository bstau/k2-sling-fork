/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.usling.webapp.integrationtest.ujax;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.usling.webapp.integrationtest.UslingHttpTestBase;

/** Test the ujax:sessionInfo resource */

public class UjaxSessionInfoTest extends UslingHttpTestBase {
    
    public void testNothing() {
        // TODO remove this all TODO_FAILS_ are gone 
    }

    public void TODO_FAILS_testSessionInfo() throws IOException {
        final String content = getContent(HTTP_BASE_URL + "/ujax:sessionInfo.json", CONTENT_TYPE_JSON);
        assertJavascript("admin.default", content, "out.println(data.userID + '.' + data.workspace)");
    }
    
    public void TODO_FAILS_testNonexistentUjaxUrl() throws IOException {
        assertHttpStatus(HTTP_BASE_URL + "/ujax:nothing", HttpServletResponse.SC_NOT_FOUND);
    }
}