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
package org.apache.sling.api.resource;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.sling.api.wrappers.ValueMapDecorator;

public class ResourceUtilTest extends TestCase {

    public void testResolveRelativeSegments() {

        assertEquals("/", ResourceUtil.normalize("/"));
        assertEquals("/", ResourceUtil.normalize("///"));

        assertEquals("/a/b/c", ResourceUtil.normalize("/a//b/c"));
        assertEquals("/a/b/c", ResourceUtil.normalize("/a/b//c"));
        assertEquals("/a/b/c", ResourceUtil.normalize("/a///b///c"));
        assertEquals("/a/b/c", ResourceUtil.normalize("/a/b/c/"));
        assertEquals("/a/b/c", ResourceUtil.normalize("/a/b/c//"));
        assertEquals("/a/b/c", ResourceUtil.normalize("/a/b/c///"));

        assertEquals("/az/bz/cz", ResourceUtil.normalize("/az//bz/cz"));
        assertEquals("/az/bz/cz", ResourceUtil.normalize("/az/bz//cz"));
        assertEquals("/az/bz/cz", ResourceUtil.normalize("/az///bz///cz"));
        assertEquals("/az/bz/cz", ResourceUtil.normalize("/az/bz/cz/"));
        assertEquals("/az/bz/cz", ResourceUtil.normalize("/az/bz/cz//"));
        assertEquals("/az/bz/cz", ResourceUtil.normalize("/az/bz/cz///"));

        assertEquals("/a", ResourceUtil.normalize("/a"));
        assertEquals("/a", ResourceUtil.normalize("//a"));
        assertEquals("/a", ResourceUtil.normalize("///a"));

        assertEquals("/az", ResourceUtil.normalize("/az"));
        assertEquals("/az", ResourceUtil.normalize("//az"));
        assertEquals("/az", ResourceUtil.normalize("///az"));

        assertEquals("/", ResourceUtil.normalize("/."));
        assertEquals("/a", ResourceUtil.normalize("/a/."));
        assertEquals("/a", ResourceUtil.normalize("/./a"));
        assertEquals("/a/b", ResourceUtil.normalize("/a/./b"));
        assertEquals("/a/b", ResourceUtil.normalize("/a/b/."));
        assertEquals("/a/b", ResourceUtil.normalize("/a/./b/."));

        assertEquals("/", ResourceUtil.normalize("/."));
        assertEquals("/az", ResourceUtil.normalize("/az/."));
        assertEquals("/az", ResourceUtil.normalize("/./az"));
        assertEquals("/az/bz", ResourceUtil.normalize("/az/./bz"));
        assertEquals("/az/bz", ResourceUtil.normalize("/az/bz/."));
        assertEquals("/az/bz", ResourceUtil.normalize("/az/./bz/."));

        assertNull(ResourceUtil.normalize("/.."));
        assertNull(ResourceUtil.normalize("/.."));
        assertEquals("/", ResourceUtil.normalize("/a/.."));
        assertEquals("/a", ResourceUtil.normalize("/a/b/.."));
        assertEquals("/", ResourceUtil.normalize("/a/b/../.."));
        assertNull(ResourceUtil.normalize("/a/b/../../.."));

        assertNull(ResourceUtil.normalize("/.."));
        assertNull(ResourceUtil.normalize("/.."));
        assertEquals("/", ResourceUtil.normalize("/az/.."));
        assertEquals("/az", ResourceUtil.normalize("/az/bz/.."));
        assertEquals("/", ResourceUtil.normalize("/az/bz/../.."));
        assertNull(ResourceUtil.normalize("/az/bz/../../.."));

        assertEquals("/b", ResourceUtil.normalize("/a/../b"));
        assertEquals("/a/c", ResourceUtil.normalize("/a/b/../c"));
        assertEquals("/c", ResourceUtil.normalize("/a/b/../../c"));
        assertNull(ResourceUtil.normalize("/a/b/../../../c"));

        assertEquals("/bz", ResourceUtil.normalize("/az/../bz"));
        assertEquals("/az/cz", ResourceUtil.normalize("/az/bz/../cz"));
        assertEquals("/cz", ResourceUtil.normalize("/az/bz/../../cz"));
        assertNull(ResourceUtil.normalize("/az/bz/../../../cz"));

        assertEquals("/...", ResourceUtil.normalize("/..."));
        assertEquals("/a/...", ResourceUtil.normalize("/a/..."));
        assertEquals("/a/b/...", ResourceUtil.normalize("/a/b/..."));

        assertEquals("/az/...", ResourceUtil.normalize("/az/..."));
        assertEquals("/az/bz/...", ResourceUtil.normalize("/az/bz/..."));

        try {
            ResourceUtil.normalize(null);
            fail("Resolving null expects NullPointerException");
        } catch (NullPointerException npe) {
            // expected
        }
    }

    public void testResolveRelativeSegmentsRelative() {
        assertEquals("a/b", ResourceUtil.normalize("a/b"));
        assertEquals("a", ResourceUtil.normalize("a/b/.."));

        assertEquals("b", ResourceUtil.normalize("a/../b"));
        assertEquals("a/c", ResourceUtil.normalize("a/b/../c"));
        assertEquals("c", ResourceUtil.normalize("a/b/../../c"));
        assertEquals("", ResourceUtil.normalize("a/b/../.."));
        assertEquals("a/c/d", ResourceUtil.normalize("a/b/../c/d"));
        assertNull(ResourceUtil.normalize("a/b/../../../c"));

        assertEquals("a/b/c", ResourceUtil.normalize("a/b/c"));
        assertEquals("az/bz/cz", ResourceUtil.normalize("az/bz/cz"));
        assertEquals("", ResourceUtil.normalize(""));
    }

    public void testGetParent() {
        assertNull(ResourceUtil.getParent("/"));
        assertNull(ResourceUtil.getParent("/.."));

        assertEquals("/", ResourceUtil.getParent("/b"));
        assertEquals("b/c", ResourceUtil.getParent("b/c/d"));
        assertEquals("/b/c", ResourceUtil.getParent("/b/c/d"));

        assertNull(ResourceUtil.getParent("b"));
        assertNull(ResourceUtil.getParent("/b/.."));
    }

    public void testGetName() {
        assertEquals("", ResourceUtil.getName("/"));
        assertEquals("", ResourceUtil.getName("/a/.."));

        assertEquals("c", ResourceUtil.getName("c"));
        assertEquals("c", ResourceUtil.getName("/c"));

        assertEquals("c", ResourceUtil.getName("b/c"));
        assertEquals("c", ResourceUtil.getName("/b/c"));

        assertEquals("c", ResourceUtil.getName("b/c/"));
        assertEquals("c", ResourceUtil.getName("/b/c/"));

        assertEquals("b", ResourceUtil.getName("b/c/.."));
        assertEquals("b", ResourceUtil.getName("/b/c/.."));
        assertEquals("", ResourceUtil.getName("/b/c/../.."));
    }
    
    public void test_getValueMap_null_resource() {
        final ValueMap valueMap = ResourceUtil.getValueMap(null);
        assertNotNull(valueMap);
        assertEquals(0, valueMap.size());
        
        final Object replaced = valueMap.put("sample", 1);
        assertNull(replaced);
        
        assertEquals(1, valueMap.size());
        assertEquals(1, valueMap.get("sample"));
        assertEquals(Integer.valueOf(1), valueMap.get("sample", Integer.class));
        assertEquals("1", valueMap.get("sample", String.class));
    }
    
    public void test_getValueMap_direct() {
        final ValueMap valueMap = new ValueMapDecorator(new HashMap<String, Object>());
        valueMap.put("sample", true);
        final Resource resource = new SyntheticResource(null, "/", "sample") {
            @Override
            @SuppressWarnings("unchecked")
            public <Type> Type adaptTo(Class<Type> type) {
                if (type == ValueMap.class) {
                    return (Type) valueMap;
                }
                
                return super.adaptTo(type);
            }
        };
        
        final ValueMap adapted = ResourceUtil.getValueMap(resource);
        assertEquals(valueMap, adapted);
        assertNotNull(adapted);
        assertEquals(1, adapted.size());

        assertEquals(true, adapted.get("sample"));
        assertEquals(Boolean.valueOf(true), adapted.get("sample", Boolean.class));
        assertEquals(Boolean.TRUE.toString(), adapted.get("sample", String.class));
    }
    
    public void test_getValueMap_decorated_map() {
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("sample", true);
        final Resource resource = new SyntheticResource(null, "/", "sample") {
            @Override
            @SuppressWarnings("unchecked")
            public <Type> Type adaptTo(Class<Type> type) {
                if (type == Map.class) {
                    return (Type) map;
                }
                
                return super.adaptTo(type);
            }
        };
        
        final ValueMap adapted = ResourceUtil.getValueMap(resource);
        assertNotNull(adapted);
        assertEquals(1, adapted.size());

        assertEquals(true, adapted.get("sample"));
        assertEquals(Boolean.valueOf(true), adapted.get("sample", Boolean.class));
        assertEquals(Boolean.TRUE.toString(), adapted.get("sample", String.class));
    }

    public void test_getValueMap_no_adapter() {
        final ValueMap valueMap = ResourceUtil.getValueMap(null);
        assertNotNull(valueMap);
        assertEquals(0, valueMap.size());
        
        final Object replaced = valueMap.put("sample", 1);
        assertNull(replaced);
        
        assertEquals(1, valueMap.size());
        assertEquals(1, valueMap.get("sample"));
        assertEquals(Integer.valueOf(1), valueMap.get("sample", Integer.class));
        assertEquals("1", valueMap.get("sample", String.class));
    }
}
