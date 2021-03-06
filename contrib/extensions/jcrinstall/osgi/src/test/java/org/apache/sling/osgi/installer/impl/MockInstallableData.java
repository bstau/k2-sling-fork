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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.sling.osgi.installer.InstallableData;

public class MockInstallableData implements InstallableData {

	private final InputStream inputStream;
	private long lastModified;
	private String digest;
	private static int counter;
	
    public MockInstallableData(String uri) {
        this(uri, uri);
    }
    
	public MockInstallableData(String uri, String data) {
        inputStream = new ByteArrayInputStream(data.getBytes());
        lastModified = System.currentTimeMillis() + counter;
        counter++;
        digest = String.valueOf(lastModified);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof MockInstallableData) {
			final MockInstallableData other = (MockInstallableData)obj;
			return digest.equals(other.digest);
		}
		return false;
	}
	
	public long getLastModified() {
		return lastModified;
	}

	@Override
	public int hashCode() {
		return digest.hashCode();
	}

	@SuppressWarnings("unchecked")
	public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
		if(type.equals(InputStream.class)) {
			return (AdapterType)inputStream;
		}
		return null;
	}

	void setDigest(String d) {
		digest = d;
	}
	
	public String getDigest() {
		return digest;
	}

    public int getBundleStartLevel() {
        return 0;
    }
}
