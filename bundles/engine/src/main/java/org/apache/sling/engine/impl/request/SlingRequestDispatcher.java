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
package org.apache.sling.engine.impl.request;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.jcr.resource.JcrResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlingRequestDispatcher implements RequestDispatcher {

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Resource resource;

    private RequestDispatcherOptions options;

    private String path;

    public SlingRequestDispatcher(String path, RequestDispatcherOptions options) {
        this.path = path;
        this.options = options;
        this.resource = null;
    }

    public SlingRequestDispatcher(Resource resource,
            RequestDispatcherOptions options) {

        this.resource = resource;
        this.options = options;
        this.path = resource.getPath();
    }

    public void include(ServletRequest request, ServletResponse sResponse)
            throws ServletException, IOException {

        // TODO: set the javax.servlet.include.* attributes

        try {

            dispatch(request, sResponse);

        } finally {

            // TODO: reset the javax.servlet.include.* attributes

        }

    }

    public void forward(ServletRequest request, ServletResponse response)
            throws ServletException, IOException {

        // fail forwarding if the response has already been committed
        if (response.isCommitted()) {
            throw new IllegalStateException("Response already committed");
        }

        // reset the response, will throw an IllegalStateException
        // if already committed, which will not be the case because
        // we already tested for this condition
        response.reset();

        // now just include as normal
        dispatch(request, response);

        // finally, we would have to ensure the response is committed
        // and closed. Let's just flush the buffer and thus commit the
        // response for now
        response.flushBuffer();
    }

    private String getAbsolutePath(SlingHttpServletRequest request, String path) {
        // path is already absolute
        if (path.startsWith("/")) {
            return path;
        }

        // get parent of current request
        String uri = request.getResource().getPath();
        int lastSlash = uri.lastIndexOf('/');
        if (lastSlash >= 0) {
            uri = uri.substring(0, lastSlash);
        }

        // append relative path to parent
        return uri + '/' + path;
    }

    private void dispatch(ServletRequest request,
            ServletResponse sResponse) throws ServletException, IOException {

        /**
         * TODO: I have made some quick fixes in this method for SLING-221 and
         * SLING-222, but haven't had time to do a proper review. This method
         * might deserve a more extensive rewrite.
         */

        SlingHttpServletRequest cRequest = RequestData.unwrap(request);
        RequestData rd = RequestData.getRequestData(cRequest);
        String absPath = getAbsolutePath(cRequest, path);

        // if the response is not an HttpServletResponse, fail gracefully not
        // doing anything
        if (!(sResponse instanceof HttpServletResponse)) {
            log.error("include: Failed to include {}, response has wrong type",
                absPath);
            return;
        }

        final HttpServletResponse response = (HttpServletResponse) sResponse;

        if (resource == null) {

            // resolve the absolute path in the resource resolver, using
            // only those parts of the path as if it would be request path
            resource = cRequest.getResourceResolver().resolve(absPath);

            // if the resource could not be resolved, fail gracefully
            if (resource == null) {
                log.error(
                    "include: Could not resolve {} to a resource, not including",
                    absPath);
                return;
            }
        }

        // ensure request path info and optional merges
        SlingRequestPathInfo info = new SlingRequestPathInfo(resource);
        info = info.merge(cRequest.getRequestPathInfo());

        // merge request dispatcher options and resource type overwrite
        if (options != null) {
            info = info.merge(options);

            // ensure overwritten resource type
            String rtOverwrite = options.getForceResourceType();
            if (rtOverwrite != null
                && !rtOverwrite.equals(resource.getResourceType())) {
                resource = new TypeOverwritingResourceWrapper(resource,
                    rtOverwrite);
            }
        }

        cRequest.getRequestProgressTracker().log(
            "Including resource {0} ({1})", resource, info);
        rd.getSlingMainServlet().includeContent(request, response, resource,
            info);
    }

    private static class TypeOverwritingResourceWrapper extends ResourceWrapper {

        /** marker value for the resourceSupertType before trying to evaluate */
        private static final String UNSET_RESOURCE_SUPER_TYPE = "<unset>";

        private final String resourceType;

        private String resourceSuperType;

        TypeOverwritingResourceWrapper(Resource delegatee, String resourceType) {
            super(delegatee);
            this.resourceType = resourceType;
            this.resourceSuperType = UNSET_RESOURCE_SUPER_TYPE;
        }

        public String getResourceType() {
            return resourceType;
        }

        /**
         * Overwrite this here because the wrapped resource will return a super
         * type based on the resource type of the wrapped resource instead of
         * the resource type overwritten here
         */
        @Override
        public String getResourceSuperType() {
            if (resourceSuperType == UNSET_RESOURCE_SUPER_TYPE) {
                resourceSuperType = JcrResourceUtil.getResourceSuperType(this);
            }
            return resourceSuperType;
        }
    }
}