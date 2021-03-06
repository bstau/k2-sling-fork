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
package org.apache.sling.jcr.contentloader.internal;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

/**
 * The <code>ContentLoader</code> creates the nodes and properties.
 * @since 2.0.4
 */
public class DefaultContentCreator implements ContentCreator {

    private PathEntry configuration;

    private final Stack<Node> parentNodeStack = new Stack<Node>();

    /** The list of versionables. */
    private final List<Node> versionables = new ArrayList<Node>();

    /** Delayed references during content loading for the reference property. */
    private final Map<String, List<String>> delayedReferences = new HashMap<String, List<String>>();
    private final Map<String, String[]> delayedMultipleReferences = new HashMap<String, String[]>();

    private String defaultRootName;

    private Node rootNode;

    private boolean isRootNodeImport;

    private boolean ignoreOverwriteFlag = false;

    // default content type for createFile()
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** Helper class to get the mime type of a file. */
    private final ContentLoaderService jcrContentHelper;

    /** List of active import providers mapped by extension. */
    private Map<String, ImportProvider> importProviders;

    /** Optional list of created nodes (for uninstall) */
    private List<String> createdNodes;

    /**
     * Constructor.
     * @param jcrContentHelper Helper class to get the mime type of a file
     */
    public DefaultContentCreator(ContentLoaderService jcrContentHelper) {
        this.jcrContentHelper = jcrContentHelper;
    }

    /**
     * Initialize this component.
     * @param pathEntry The configuration for this import.
     * @param defaultImportProviders List of all import providers.
     * @param createdNodes Optional list to store new nodes (for uninstall)
     */
    public void init(final PathEntry pathEntry,
                     final Map<String, ImportProvider> defaultImportProviders,
                     final List<String> createdNodes) {
        this.configuration = pathEntry;
        // create list of allowed import providers
        this.importProviders = new HashMap<String, ImportProvider>();
        final Iterator<Map.Entry<String, ImportProvider>> entryIter = defaultImportProviders.entrySet().iterator();
        while ( entryIter.hasNext() ) {
            final Map.Entry<String, ImportProvider> current = entryIter.next();
            if (!configuration.isIgnoredImportProvider(current.getKey()) ) {
                importProviders.put(current.getKey(), current.getValue());
            }
        }
        this.createdNodes = createdNodes;
    }

    /**
     *
     * If the defaultRootName is null, we are in ROOT_NODE import mode.
     * @param parentNode
     * @param defaultRootName
     */
    public void prepareParsing(final Node parentNode,
                               final String defaultRootName) {
        this.parentNodeStack.clear();
        this.parentNodeStack.push(parentNode);
        this.defaultRootName = defaultRootName;
        this.rootNode = null;
        isRootNodeImport = defaultRootName == null;
    }

    /**
     * Get the list of versionable nodes.
     */
    public List<Node> getVersionables() {
        return this.versionables;
    }

    /**
     * Clear the content loader.
     */
    public void clear() {
        this.versionables.clear();
    }

    /**
     * Set the ignore overwrite flag.
     * @param flag
     */
    public void setIgnoreOverwriteFlag(boolean flag) {
        this.ignoreOverwriteFlag = flag;
    }

    /**
     * Get the created root node.
     */
    public Node getRootNode() {
        return this.rootNode;
    }

    /**
     * Get all active import providers.
     * @return A map of providers
     */
    public Map<String, ImportProvider> getImportProviders() {
        return this.importProviders;
    }

    /**
     * Return the import provider for the name
     * @param name The file name.
     * @return The provider or <code>null</code>
     */
    public ImportProvider getImportProvider(String name) {
        ImportProvider provider = null;
        final Iterator<String> ipIter = importProviders.keySet().iterator();
        while (provider == null && ipIter.hasNext()) {
            final String ext = ipIter.next();
            if (name.endsWith(ext)) {
                provider = importProviders.get(ext);
            }
        }
        return provider;
    }

    /**
     * Get the extension of the file name.
     * @param name The file name.
     * @return The extension a provider is registered for - or <code>null</code>
     */
    public String getImportProviderExtension(String name) {
        String providerExt = null;
        final Iterator<String> ipIter = importProviders.keySet().iterator();
        while (providerExt == null && ipIter.hasNext()) {
            final String ext = ipIter.next();
            if (name.endsWith(ext)) {
                providerExt = ext;
            }
        }
        return providerExt;
    }

    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentCreator#createNode(java.lang.String, java.lang.String, java.lang.String[])
     */
    public void createNode(String name,
                           String primaryNodeType,
                           String[] mixinNodeTypes)
    throws RepositoryException {
        final Node parentNode = this.parentNodeStack.peek();
        if ( name == null ) {
            if ( this.parentNodeStack.size() > 1 ) {
                throw new RepositoryException("Node needs to have a name.");
            }
            name = this.defaultRootName;
        }

        // if we are in root node import mode, we don't create the root top level node!
        if ( !isRootNodeImport || this.parentNodeStack.size() > 1 ) {
            // if node already exists but should be overwritten, delete it
            if (!this.ignoreOverwriteFlag && this.configuration.isOverwrite() && parentNode.hasNode(name)) {
                parentNode.getNode(name).remove();
            }

            // ensure repository node
            Node node;
            if (parentNode.hasNode(name)) {

                // use existing node
                node = parentNode.getNode(name);
            } else if (primaryNodeType == null) {

                // no explicit node type, use repository default
                node = parentNode.addNode(name);
                if ( this.createdNodes != null ) {
                    this.createdNodes.add(node.getPath());
                }

            } else {

                // explicit primary node type
                node = parentNode.addNode(name, primaryNodeType);
                if ( this.createdNodes != null ) {
                    this.createdNodes.add(node.getPath());
                }
            }

            // ammend mixin node types
            if (mixinNodeTypes != null) {
                for (final String mixin : mixinNodeTypes) {
                    if (!node.isNodeType(mixin)) {
                        node.addMixin(mixin);
                    }
                }
            }

            // check if node is versionable
            final boolean addToVersionables = this.configuration.isCheckin()
                                        && node.isNodeType("mix:versionable");
            if ( addToVersionables ) {
                this.versionables.add(node);
            }

            this.parentNodeStack.push(node);
            if ( this.rootNode == null ) {
                this.rootNode = node;
            }
        }
    }

    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentCreator#createProperty(java.lang.String, int, java.lang.String)
     */
    public void createProperty(String name, int propertyType, String value)
    throws RepositoryException {
        final Node node = this.parentNodeStack.peek();
        // check if the property already exists, don't overwrite it in this case
        if (node.hasProperty(name)
            && !node.getProperty(name).isNew()) {
            return;
        }

        if ( propertyType == PropertyType.REFERENCE ) {
            // need to resolve the reference
            String propPath = node.getPath() + "/" + name;
            String uuid = getUUID(node.getSession(), propPath, getAbsPath(node, value));
            if (uuid != null) {
                node.setProperty(name, uuid, propertyType);
            }

        } else if ("jcr:isCheckedOut".equals(name)) {

            // don't try to write the property but record its state
            // for later checkin if set to false
            final boolean checkedout = Boolean.valueOf(value);
            if (!checkedout) {
                if ( !this.versionables.contains(node) ) {
                    this.versionables.add(node);
                }
            }
        } else {
            node.setProperty(name, value, propertyType);
        }
    }

    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentCreator#createProperty(java.lang.String, int, java.lang.String[])
     */
    public void createProperty(String name, int propertyType, String[] values)
    throws RepositoryException {
        final Node node = this.parentNodeStack.peek();
        // check if the property already exists, don't overwrite it in this case
        if (node.hasProperty(name)
            && !node.getProperty(name).isNew()) {
            return;
        }
        if ( propertyType == PropertyType.REFERENCE ) {
            String propPath = node.getPath() + "/" + name;

            boolean hasAll = true;
            String[] uuids = new String[values.length];
            String[] uuidOrPaths = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                uuids[i] = getUUID(node.getSession(), propPath, getAbsPath(node, values[i]));
                uuidOrPaths[i] = uuids[i] != null ? uuids[i] : getAbsPath(node, values[i]);
                if (uuids[i] == null) hasAll = false;
            }

            node.setProperty(name, uuids, propertyType);

            if (!hasAll) {
                delayedMultipleReferences.put(propPath, uuidOrPaths);
            }
        } else {
            node.setProperty(name, values, propertyType);
        }
    }

    protected Value createValue(final ValueFactory factory, Object value) {
        if ( value == null ) {
            return null;
        }
        if ( value instanceof Long ) {
            return factory.createValue((Long)value);
        } else if ( value instanceof Date ) {
            final Calendar c = Calendar.getInstance();
            c.setTime((Date)value);
            return factory.createValue(c);
        } else if ( value instanceof Calendar ) {
            return factory.createValue((Calendar)value);
        } else if ( value instanceof Double ) {
            return factory.createValue((Double)value);
        } else if ( value instanceof Boolean ) {
            return factory.createValue((Boolean)value);
        } else if ( value instanceof InputStream ) {
            return factory.createValue((InputStream)value);
        } else {
            return factory.createValue(value.toString());
        }

    }
    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentCreator#createProperty(java.lang.String, java.lang.Object)
     */
    public void createProperty(String name, Object value)
    throws RepositoryException {
        final Node node = this.parentNodeStack.peek();
        // check if the property already exists, don't overwrite it in this case
        if (node.hasProperty(name)
            && !node.getProperty(name).isNew()) {
            return;
        }
        if ( value == null ) {
            if ( node.hasProperty(name) ) {
                node.getProperty(name).remove();
            }
        } else {
            final Value jcrValue = this.createValue(node.getSession().getValueFactory(), value);
            node.setProperty(name, jcrValue);
        }
    }

    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentCreator#createProperty(java.lang.String, java.lang.Object[])
     */
    public void createProperty(String name, Object[] values)
    throws RepositoryException {
        final Node node = this.parentNodeStack.peek();
        // check if the property already exists, don't overwrite it in this case
        if (node.hasProperty(name)
            && !node.getProperty(name).isNew()) {
            return;
        }
        if ( values == null || values.length == 0 ) {
            if ( node.hasProperty(name) ) {
                node.getProperty(name).remove();
            }
        } else {
            final Value[] jcrValues = new Value[values.length];
            for(int i = 0; i < values.length; i++) {
                jcrValues[i] = this.createValue(node.getSession().getValueFactory(), values[i]);
            }
            node.setProperty(name, jcrValues);
        }
    }

    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentCreator#finishNode()
     */
    public void finishNode()
    throws RepositoryException {
        final Node node = this.parentNodeStack.pop();
        // resolve REFERENCE property values pointing to this node
        resolveReferences(node);
    }

    private String getAbsPath(Node node, String path) throws RepositoryException {
        if (path.startsWith("/")) return path;

        while (path.startsWith("../")) {
            path = path.substring(3);
            node = node.getParent();
        }

        while (path.startsWith("./")) {
            path = path.substring(2);
        }

        return node.getPath() + "/" + path;
    }

    private String getUUID(Session session, String propPath,
                          String referencePath)
    throws RepositoryException {
        if (session.itemExists(referencePath)) {
            Item item = session.getItem(referencePath);
            if (item.isNode()) {
                Node refNode = (Node) item;
                if (refNode.isNodeType("mix:referenceable")) {
                    return refNode.getUUID();
                }
            }
        } else {
            // not existing yet, keep for delayed setting
            List<String> current = delayedReferences.get(referencePath);
            if (current == null) {
                current = new ArrayList<String>();
                delayedReferences.put(referencePath, current);
            }
            current.add(propPath);
        }

        // no UUID found
        return null;
    }

    private void resolveReferences(Node node) throws RepositoryException {
        List<String> props = delayedReferences.remove(node.getPath());
        if (props == null || props.size() == 0) {
            return;
        }

        // check whether we can set at all
        if (!node.isNodeType("mix:referenceable")) {
            return;
        }

        Session session = node.getSession();
        String uuid = node.getUUID();

        for (String property : props) {
            String name = getName(property);
            Node parentNode = getParentNode(session, property);
            if (parentNode != null) {
                if (parentNode.hasProperty(name) && parentNode.getProperty(name).getDefinition().isMultiple()) {
                    boolean hasAll = true;
                    String[] uuidOrPaths = delayedMultipleReferences.get(property);
                    String[] uuids = new String[uuidOrPaths.length];
                    for (int i = 0; i < uuidOrPaths.length; i++) {
                        // is the reference still a path
                        if (uuidOrPaths[i].startsWith("/")) {
                            if (uuidOrPaths[i].equals(node.getPath())) {
                                uuidOrPaths[i] = uuid;
                                uuids[i] = uuid;
                            } else {
                                uuids[i] = null;
                                hasAll = false;
                            }
                        } else {
                            uuids[i] = uuidOrPaths[i];
                        }
                    }
                    parentNode.setProperty(name, uuids, PropertyType.REFERENCE);

                    if (hasAll) {
                        delayedMultipleReferences.remove(property);
                    }
                } else {
                    parentNode.setProperty(name, uuid, PropertyType.REFERENCE);
                }
            }
        }
    }

    /**
     * Gets the name part of the <code>path</code>. The name is
     * the part of the path after the last slash (or the complete path if no
     * slash is contained).
     *
     * @param path The path from which to extract the name part.
     * @return The name part.
     */
    private String getName(String path) {
        int lastSlash = path.lastIndexOf('/');
        String name = (lastSlash < 0) ? path : path.substring(lastSlash + 1);

        return name;
    }

    private Node getParentNode(Session session, String path)
            throws RepositoryException {
        int lastSlash = path.lastIndexOf('/');

        // not an absolute path, cannot find parent
        if (lastSlash < 0) {
            return null;
        }

        // node below root
        if (lastSlash == 0) {
            return session.getRootNode();
        }

        // item in the hierarchy
        path = path.substring(0, lastSlash);
        if (!session.itemExists(path)) {
            return null;
        }

        Item item = session.getItem(path);
        return (item.isNode()) ? (Node) item : null;
    }


    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentCreator#createFileAndResourceNode(java.lang.String, java.io.InputStream, java.lang.String, long)
     */
    public void createFileAndResourceNode(String name,
                                          InputStream data,
                                          String mimeType,
                                          long lastModified)
    throws RepositoryException {
        int lastSlash = name.lastIndexOf('/');
        name = (lastSlash < 0) ? name : name.substring(lastSlash + 1);
        final Node parentNode = this.parentNodeStack.peek();

        // if node already exists but should be overwritten, delete it
        if (this.configuration.isOverwrite() && parentNode.hasNode(name)) {
            parentNode.getNode(name).remove();
        } else if (parentNode.hasNode(name)) {
            this.parentNodeStack.push(parentNode.getNode(name));
            this.parentNodeStack.push(parentNode.getNode(name).getNode("jcr:content"));
            return;
        }

        // ensure content type
        if (mimeType == null) {
            mimeType = jcrContentHelper.getMimeType(name);
            if (mimeType == null) {
                jcrContentHelper.log.info(
                    "createFile: Cannot find content type for {}, using {}",
                    name, DEFAULT_CONTENT_TYPE);
                mimeType = DEFAULT_CONTENT_TYPE;
            }
        }

        // ensure sensible last modification date
        if (lastModified <= 0) {
            lastModified = System.currentTimeMillis();
        }

        this.createNode(name, "nt:file", null);
        this.createNode("jcr:content", "nt:resource", null);
        this.createProperty("jcr:mimeType", mimeType);
        this.createProperty("jcr:lastModified", lastModified);
        this.createProperty("jcr:data", data);
    }

    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentCreator#switchCurrentNode(java.lang.String, java.lang.String)
     */
    public boolean switchCurrentNode(String subPath, String newNodeType)
    throws RepositoryException {
        if ( subPath.startsWith("/") ) {
            subPath = subPath.substring(1);
        }
        final StringTokenizer st = new StringTokenizer(subPath, "/");
        Node node = this.parentNodeStack.peek();
        while ( st.hasMoreTokens() ) {
            final String token = st.nextToken();
            if ( !node.hasNode(token) ) {
                if ( newNodeType == null ) {
                    return false;
                }
                final Node n = node.addNode(token, newNodeType);
                if ( this.createdNodes != null ) {
                    this.createdNodes.add(n.getPath());
                }
            }
            node = node.getNode(token);
        }
        this.parentNodeStack.push(node);
        return true;
    }

}
