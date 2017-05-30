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

class CifsStorageProvider extends StorageProvider {

	static String providerName = "cifs"

	String username
	String password = ''
	String domain = ''
	String host
	String baseUrl
	NtlmPasswordAuthentication cifsAuthentication

	public CifsStorageProvider(Map options) {
		host = options.host
		baseUrl  = options.baseUrl  ?: baseUrl
		username = opts.username ?: username

		password = opts.password ?: password
		domain = opts.domain ?: domain
		if(options.defaultFileACL) {
			//this.defaultFileACL = options.defaultFileACL
		}
	}

	NtlmPasswordAuthentication getCifsAuthentication() {
		if(cifsAuthentication == null && username != null) {
			cifsAuthentication = new NtlmPasswordAuthentication(domain, username, password)
		}
		return cifsAuthentication
	}

	Directory getDirectory(String name) {
		new CifsDirectory(name:name, provider:this)
	}

	public String getSmbUrl(path=null) {
		"smb://${host}/${path ? path : ''}"
	}

	def getDirectories() {
		def directories = []
		def cifsAuth = getCifsAuthentication()
		def baseDirectory
		if(cifsAuth)
			baseDirectory = new SmbFile(smbUrl, cifsAuth)
		else
			baseDirectory = new SmbFile(smbUrl)

		baseDirectory.listFiles()?.each { file ->
			if(file.isDirectory())
				directories << new CifsDirectory(name:file.name, provider:this)
		}
		return directories
	}

}
