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
package org.apache.sling.osgi.installer.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.apache.sling.osgi.installer.OsgiResourceProcessor;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.runner.RunWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkListener;

@RunWith(JMock.class)
public class OsgiResourceProcessorListTest {
  
    private final Mockery mockery = new Mockery();

    @org.junit.Test public void testNoProcessors() throws Exception {
        final BundleContext bc = mockery.mock(BundleContext.class);
        mockery.checking(new Expectations() {{
        	allowing(bc).addFrameworkListener(with(any(FrameworkListener.class)));
        }});
        final OsgiResourceProcessorList c = new OsgiResourceProcessorList(bc, null, null, null);
        c.clear();
        assertNull("OsgiResourceProcessorList must return null processor for null uri", c.getProcessor(null, null));
        assertNull("OsgiResourceProcessorList must return null processor for TEST uri", c.getProcessor("TEST", null));
    }
    
    @org.junit.Test public void testTwoProcessors() throws Exception {
        final BundleContext bc = mockery.mock(BundleContext.class);
        final OsgiResourceProcessor p1 = mockery.mock(OsgiResourceProcessor.class);
        final OsgiResourceProcessor p2 = mockery.mock(OsgiResourceProcessor.class);
        
        mockery.checking(new Expectations() {{
        	allowing(bc).addFrameworkListener(with(any(FrameworkListener.class)));
            allowing(p1).canProcess("foo", null) ; will(returnValue(true));
            allowing(p1).canProcess("bar", null) ; will(returnValue(false));
            allowing(p2).canProcess("foo", null) ; will(returnValue(false));
            allowing(p2).canProcess("bar", null) ; will(returnValue(true));
        }});
        
        final OsgiResourceProcessorList c = new OsgiResourceProcessorList(bc, null, null, null); 
        c.clear();
        c.add(p1);
        c.add(p2);
        
        assertEquals("foo extension must return processor p1", p1, c.getProcessor("foo", null));
        assertEquals("bar extension must return processor p2", p2, c.getProcessor("bar", null));
    }
}
