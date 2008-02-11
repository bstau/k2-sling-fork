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
package org.apache.sling.threads.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.threads.ThreadPool;
import org.apache.sling.threads.ThreadPoolManager;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DefaultThreadPoolManager implements the {@link ThreadPoolManager} interface
 * and is responsible to manage {@link ThreadPool}s.
 *
 * @scr.component metatype="false"
 * @scr.service interface="org.apache.sling.threads.ThreadPoolManager"
 *
 * @version $Id$
 */
public class DefaultThreadPoolManager implements ThreadPoolManager {

    /** The default queue size */
    protected final static int DEFAULT_QUEUE_SIZE = -1;

    /** The default maximum pool size */
    protected final static int DEFAULT_MAX_POOL_SIZE = 5;

    /** The default minimum pool size */
    protected final static int DEFAULT_MIN_POOL_SIZE = 5;

    /** The default thread priority */
    protected final static int DEFAULT_THREAD_PRIORITY =  Thread.NORM_PRIORITY;

    /** The default daemon mode */
    protected final static boolean DEFAULT_DAEMON_MODE = false;

    /** The default keep alive time */
    protected final static long DEFAULT_KEEP_ALIVE_TIME = 60000L;

    /** The default way to shutdown gracefully */
    protected final static boolean DEFAULT_SHUTDOWN_GRACEFUL = false;

    /** The default shutdown waittime time */
    protected final static int DEFAULT_SHUTDOWN_WAIT_TIME = -1;

    /** The default shutdown waittime time */
    protected final static String DEFAULT_THREADPOOL_NAME = "default";

    /** ThreadPool block policy ABORT */
    protected final static String POLICY_ABORT = "ABORT";

    /** ThreadPool block policy DISCARD */
    protected final static String POLICY_DISCARD = "DISCARD";

    /** ThreadPool block policy DISCARD-OLDEST */
    protected final static String POLICY_DISCARD_OLDEST = "DISCARDOLDEST";

    /** ThreadPool block policy RUN */
    protected final static String POLICY_RUN = "RUN";

    /** The default shutdown waittime time */
    protected final static String DEFAULT_BLOCK_POLICY = POLICY_RUN;

    /** By default we use the logger for this class. */
    protected Logger logger = LoggerFactory.getLogger(getClass());

    /** The managed thread pools */
    protected final Map<String, ThreadPool> pools = new HashMap<String, ThreadPool>();

    /**
     * Activate this component.
     */
    protected void activate(ComponentContext context) throws Exception {
        this.logger.info("Starting thread pool manager.");
        final ThreadPool defaultPool = new DefaultThreadPool(
                    DEFAULT_THREADPOOL_NAME,
                    DEFAULT_MIN_POOL_SIZE,
                    DEFAULT_MAX_POOL_SIZE,
                    DEFAULT_QUEUE_SIZE,
                    DEFAULT_KEEP_ALIVE_TIME,
                    DEFAULT_BLOCK_POLICY,
                    DEFAULT_SHUTDOWN_GRACEFUL,
                    DEFAULT_SHUTDOWN_WAIT_TIME,
                    null,
                    DEFAULT_THREAD_PRIORITY,
                    DEFAULT_DAEMON_MODE);
        this.pools.put(defaultPool.getName(), defaultPool);
        this.logger.info("Thread pool manager startet with default pool.");
    }

    /**
     * Deactivate this component.
     */
    protected void deactivate(ComponentContext context) throws Exception {
        this.logger.info("Stopping thread pool manager.");
        this.logger.debug("Disposing all thread pools");

        for (ThreadPool pool : this.pools.values()) {
            this.logger.debug("Shutting down thread pool {}", pool.getName());

            pool.shutdown();

            this.logger.debug("Thread pool " + pool.getName() + " is shut down.");
        }
        this.pools.clear();
        this.logger.info("Thread pool manager stopped.");
    }

    /**
     * @see org.apache.sling.threads.ThreadPoolManager#add(org.apache.sling.threads.ThreadPool)
     */
    public boolean add(ThreadPool pool) {
        synchronized ( this.pools ) {
            if (null != pools.get(pool.getName())) {
                return false;
            }
            pools.put(pool.getName(), pool);
        }
        return true;
    }

    /**
     * @see org.apache.sling.threads.ThreadPoolManager#get(java.lang.String)
     */
    public ThreadPool get(String name) {
        if ( name == null ) {
            name = DEFAULT_THREADPOOL_NAME;
        }
        synchronized (this.pools) {
            return this.pools.get(name);
        }
    }
}