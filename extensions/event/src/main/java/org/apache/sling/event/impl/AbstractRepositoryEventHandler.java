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
package org.apache.sling.event.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.EventListener;

import org.apache.sling.commons.threads.ThreadPool;
import org.apache.sling.commons.threads.ThreadPoolConfig;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.apache.sling.event.EventUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for all event handlers in this package.
 *
 * @scr.component abstract="true"
 * @scr.service interface="org.osgi.service.event.EventHandler"
 */
public abstract class AbstractRepositoryEventHandler
    implements EventHandler, EventListener {

    /** FIXME - This is a copy from the sling core constants to avoid
     * a dependency just for the constant. We will move this into an
     * OSGi helper bundle
     */
    public static final String SLING_ID = "sling.id";

    /** Default log. */
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** @scr.property value="/sling/events" */
    protected static final String CONFIG_PROPERTY_REPO_PATH = "repository.path";

    /** @scr.reference */
    protected SlingRepository repository;

    /** @scr.reference */
    protected EventAdmin eventAdmin;

    /** Our application id. */
    protected String applicationId;

    /** The repository session to write into the repository. */
    protected Session writerSession;

    /** The path in the repository. */
    protected String repositoryPath;

    /** Is the background task still running? */
    protected boolean running;

    /** A local queue for serialising the event processing. */
    protected final BlockingQueue<EventInfo> queue = new LinkedBlockingQueue<EventInfo>();

    /** A local queue for writing received events into the repository. */
    protected final BlockingQueue<Event> writeQueue = new LinkedBlockingQueue<Event>();

    /** @scr.reference */
    protected ThreadPoolManager threadPoolManager;

    /** Our thread pool. */
    protected ThreadPool threadPool;

    /**
     * Activate this component.
     * @param context
     * @throws RepositoryException
     */
    protected void activate(final ComponentContext context)
    throws Exception {
        this.applicationId = context.getBundleContext().getProperty(SLING_ID);
        this.repositoryPath = (String)context.getProperties().get(CONFIG_PROPERTY_REPO_PATH);

        // start background threads
        if ( this.threadPoolManager == null ) {
            throw new Exception("No ThreadPoolManager found.");
        }
        final ThreadPoolConfig config = new ThreadPoolConfig();
        config.setMinPoolSize(10);
        config.setMaxPoolSize(30);
        config.setQueueSize(-1);
        config.setShutdownGraceful(true);
        threadPoolManager.create(EventHelper.THREAD_POOL_NAME, config);
                                 ;
        this.threadPool = threadPoolManager.get(EventHelper.THREAD_POOL_NAME);
        if ( this.threadPool == null ) {
            throw new Exception("No thread pool found.");
        }
        this.running = true;
        // start writer thread
        this.threadPool.execute(new Runnable() {
            public void run() {
                try {
                    startWriterSession();
                } catch (RepositoryException e) {
                    // there is nothing we can do except log!
                    logger.error("Error during session starting.", e);
                    running = false;
                }
                try {
                    processWriteQueue();
                } catch (Throwable t) {
                    logger.error("Writer thread stopped with exception: " + t.getMessage(), t);
                    running = false;
                }
                stopWriterSession();
            }
        });
        this.threadPool.execute(new Runnable() {
            public void run() {
                try {
                    runInBackground();
                } catch (Throwable t) {
                    logger.error("Background thread stopped with exception: " + t.getMessage(), t);
                    running = false;
                }
            }
        });
    }

    protected abstract void runInBackground() throws RepositoryException;

    protected abstract void processWriteQueue();

    /**
     * Deactivate this component.
     * @param context
     */
    protected void deactivate(final ComponentContext context) {
        // stop background threads by putting empty objects into the queue
        this.running = false;
        try {
            this.writeQueue.put(new Event("some", null));
        } catch (InterruptedException e) {
            this.ignoreException(e);
        }
        try {
            this.queue.put(new EventInfo());
        } catch (InterruptedException e) {
            this.ignoreException(e);
        }
        this.threadPool = null;
    }

    /**
     * Create a new session.
     * @return
     * @throws RepositoryException
     */
    protected Session createSession()
    throws RepositoryException {
        final SlingRepository repo = this.repository;
        if ( repo == null ) {
            throw new RepositoryException("Repository is currently not available.");
        }
        return repo.loginAdministrative(null);
    }

    /**
     * Start the repository session and add this handler as an observer
     * for new events created on other nodes.
     * @throws RepositoryException
     */
    protected void startWriterSession() throws RepositoryException {
        this.writerSession = this.createSession();
        if ( this.repositoryPath != null ) {
            this.createRepositoryPath();
        }
    }

    /**
     * Stop the session.
     */
    protected void stopWriterSession() {
        if ( this.writerSession != null ) {
            try {
                this.writerSession.getWorkspace().getObservationManager().removeEventListener(this);
            } catch (RepositoryException e) {
                // we just ignore it
                this.logger.warn("Unable to remove event listener.", e);
            }
            this.writerSession.logout();
            this.writerSession = null;
        }
    }

    /**
     * Create the repository path in the repository.
     */
    protected void createRepositoryPath()
    throws RepositoryException {
        if ( !this.writerSession.itemExists(this.repositoryPath) ) {
            Node node = this.writerSession.getRootNode();
            String path = this.repositoryPath.substring(1);
            int pos = path.lastIndexOf('/');
            if ( pos != -1 ) {
                final StringTokenizer st = new StringTokenizer(path.substring(0, pos), "/");
                while ( st.hasMoreTokens() ) {
                    final String token = st.nextToken();
                    if ( !node.hasNode(token) ) {
                        node.addNode(token, "nt:folder");
                        node.save();
                    }
                    node = node.getNode(token);
                }
                path = path.substring(pos + 1);
            }
            if ( !node.hasNode(path) ) {
                node.addNode(path, this.getContainerNodeType());
                node.save();
            }
        }
    }

    protected String getContainerNodeType() {
        return EventHelper.EVENTS_NODE_TYPE;
    }

    protected String getEventNodeType() {
        return EventHelper.EVENT_NODE_TYPE;
    }

    /**
     * Write an event to the repository.
     * @param e
     * @throws RepositoryException
     * @throws IOException
     */
    protected Node writeEvent(Event e, String suggestedName)
    throws RepositoryException {
        // create new node with name of topic
        final Node rootNode = (Node) this.writerSession.getItem(this.repositoryPath);

        final String nodeType = this.getEventNodeType();
        final String nodeName;
        if ( suggestedName != null ) {
            nodeName = suggestedName;
        } else {
            final Calendar now = Calendar.getInstance();
            final int sepPos = nodeType.indexOf(':');
            nodeName = nodeType.substring(sepPos+1) + "-" + this.applicationId + "-" + now.getTime().getTime();
        }
        final Node eventNode = rootNode.addNode(nodeName, nodeType);

        eventNode.setProperty(EventHelper.NODE_PROPERTY_CREATED, Calendar.getInstance());
        eventNode.setProperty(EventHelper.NODE_PROPERTY_TOPIC, e.getTopic());
        eventNode.setProperty(EventHelper.NODE_PROPERTY_APPLICATION, this.applicationId);

        final String[] names = e.getPropertyNames();
        // we will  not write the distributable property, so length must be greater 1
        if ( names != null && names.length > 1 ) {
            // if the application property is available, we will override it
            // if it is not available we will add it
            boolean addApplication = false;
            boolean removeNotifierContextProperty = false;
            if ( e.getProperty(EventUtil.PROPERTY_APPLICATION) == null ) {
                addApplication = true;
            }
            if ( e.getProperty(EventUtil.JobStatusNotifier.CONTEXT_PROPERTY_NAME) != null ) {
                removeNotifierContextProperty = true;
            }
            try {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeInt(names.length  - 1 + (addApplication ? 1 : 0) - (removeNotifierContextProperty ? 1 : 0));
                for(int i=0;i<names.length;i++) {
                    if ( names[i].equals(EventUtil.PROPERTY_APPLICATION) ) {
                        oos.writeObject(names[i]);
                        oos.writeObject(this.applicationId);
                    } else if ( !names[i].equals(EventUtil.PROPERTY_DISTRIBUTE)
                             && !names[i].equals(EventUtil.JobStatusNotifier.CONTEXT_PROPERTY_NAME) ) {
                        oos.writeObject(names[i]);
                        oos.writeObject(e.getProperty(names[i]));
                    }
                }
                if ( addApplication ) {
                    oos.writeObject(EventUtil.PROPERTY_APPLICATION);
                    oos.writeObject(this.applicationId);
                }
                oos.close();
                eventNode.setProperty(EventHelper.NODE_PROPERTY_PROPERTIES, new ByteArrayInputStream(baos.toByteArray()));
            } catch (IOException ioe) {
                throw new RepositoryException("Unable to serialize event properties.", ioe);
            }
        }
        this.addNodeProperties(eventNode, e);
        rootNode.save();

        return eventNode;
    }

    /**
     * Read an event from the repository.
     * @return
     * @throws RepositoryException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    protected Event readEvent(Node eventNode)
    throws RepositoryException, ClassNotFoundException {
        final String topic = eventNode.getProperty(EventHelper.NODE_PROPERTY_TOPIC).getString();
        final Dictionary<String, Object> properties = new Hashtable<String, Object>();
        if ( eventNode.hasProperty(EventHelper.NODE_PROPERTY_PROPERTIES) ) {
            try {
                final ObjectInputStream ois = new ObjectInputStream(eventNode.getProperty(EventHelper.NODE_PROPERTY_PROPERTIES).getStream());
                int length = ois.readInt();
                for(int i=0;i<length;i++) {
                    final String key = (String)ois.readObject();
                    final Object value = ois.readObject();
                    properties.put(key, value);
                }
            } catch (IOException ioe) {
                throw new RepositoryException("Unable to deserialize event properties.", ioe);
            }
        }
        this.addEventProperties(eventNode, properties);
        try {
            final Event event = new Event(topic, properties);
            return event;
        } catch (IllegalArgumentException iae) {
            // this exception occurs if the topic is not correct (it should never happen,
            // but you never know)
            throw new RepositoryException("Unable to read event: " + iae.getMessage(), iae);
        }
    }

    protected void addEventProperties(Node eventNode, Dictionary<String, Object> properties)
    throws RepositoryException {
        // nothing to do
    }

    /**
     * Add properties when storing event in repository.
     * This method can be enhanced by sub classes.
     * @param eventNode
     * @param event
     * @throws RepositoryException
     */
    protected void addNodeProperties(Node eventNode, Event event)
    throws RepositoryException {
        // nothing to do here
    }

    /**
     * Helper method which just logs the exception in debug mode.
     * @param e
     */
    protected void ignoreException(Exception e) {
        if ( this.logger.isDebugEnabled() ) {
            this.logger.debug("Ignore exception " + e.getMessage(), e);
        }
    }

    protected static final class EventInfo {
        public String nodePath;
        public Event event;
    }

}