/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bertramlabs.plugins.karman.cifs

import com.bertramlabs.plugins.karman.*
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import jcifs.context.SingletonContext
import jcifs.context.BaseContext
import jcifs.CIFSContext
import jcifs.Configuration
import jcifs.config.PropertyConfiguration

class CifsStorageProvider extends StorageProvider<CifsDirectory> {

	static String providerName = "cifs"

	public String getProviderName() {
		return providerName
	}


	String username
	String password = ''
	String domain = ''
	String host
	String baseUrl
	// NtlmPasswordAuthentication cifsAuthentication
	CIFSContext cifsContext

	public CifsStorageProvider(Map options) {
		// jcifs.Config.setProperty("resolveOrder", "DNS");
		host = options.host
		baseUrl  = options.baseUrl  ?: baseUrl
		username = options.username ?: username

		password = options.password ?: password
		domain = options.domain ?: domain
		if(options.defaultFileACL) {
			//this.defaultFileACL = options.defaultFileACL
		}
	}

	// NtlmPasswordAuthentication getCifsAuthentication() {
	// 	if(cifsAuthentication == null && username != null) {
	// 		cifsAuthentication = new NtlmPasswordAuthentication(domain, username, password)
	// 	}
	// 	return cifsAuthentication
	// }

	CIFSContext getCifsContext() {
		if(cifsContext) {
			return cifsContext
		} else {
			Properties prop = new Properties();
			prop.put("resolveOrder", "DNS")
			// prop.put( "jcifs.smb.client.enableSMB2", "true");
			// prop.put( "jcifs.smb.client.disableSMB1", "false");
			// prop.put( "jcifs.traceResources", "true" );
			Configuration config = new PropertyConfiguration(prop);
			CIFSContext baseContext = new BaseContext(config);
			if(username != null) {
				CIFSContext contextWithCred = baseContext.withCredentials(new NtlmPasswordAuthentication(baseContext, domain, username, password));
				cifsContext = contextWithCred	
			} else {
				cifsContext = baseContext
			}
			return cifsContext
		}
	}

	CifsDirectory getDirectory(String name) {
		name = name.replace('\\','/')
		new CifsDirectory(name:name, provider:this)
	}

	public String getSmbUrl(String path=null) {
		"smb://${host}/${path ? (path + '/') : ''}"
	}

	List<CifsDirectory> getDirectories() {
		def directories = []
		def baseDirectory = new SmbFile(smbUrl, getCifsContext())
	

		baseDirectory.listFiles()?.each { file ->
			if(file.isDirectory())
				directories << new CifsDirectory(name:file.name, provider:this)
		}
		return directories
	}

}
