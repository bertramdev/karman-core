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

package com.bertramlabs.plugins.karman.nfs

import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import com.emc.ecs.nfsclient.nfs.NfsReaddirRequest
import com.emc.ecs.nfsclient.nfs.io.Nfs3File
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3
import com.emc.ecs.nfsclient.rpc.CredentialUnix
import groovy.util.logging.Slf4j
import groovy.util.logging.Slf4j

@Slf4j
/**
 * Provides an NFSv3 specification implementation for Karman and utilizing karman to manage files on the NFSv3 share
 * NOTE: It may be required to set the mount points on your nfs host to insecure and allow insecure ports
 * @author David Estes
 */
class NfsStorageProvider extends StorageProvider<NfsDirectory> {
	static String providerName = "nfs"

	public String getProviderName() {
		return providerName
	}

	String host
	String username
	String password
	String exportFolder
	String basePath
	String baseUrl

	public NfsStorageProvider(Map options) {
		basePath = options.basePath ?: basePath
		baseUrl  = options.baseUrl  ?: baseUrl
		if(options.defaultFileACL) {
			//	this.defaultFileACL = options.defaultFileACL
		}
		host = options.host
		username = options.username
		password = options.password
		this.exportFolder = options.exportFolder
	}
	NfsDirectory getDirectory(String name) {
		new NfsDirectory(name: name, provider: this, baseFile: new Nfs3File(getNfsClient(),name))
	}


	Nfs3 getNfsClient() {
		new Nfs3(host,exportFolder,new CredentialUnix(0,0,[] as Set),3)
	}


	List<NfsDirectory> getDirectories() {
		Nfs3 client = getNfsClient()
		def files = new Nfs3File(client,"/").listFiles()
		return files.findAll { file ->
			file.isDirectory()

		}?.collect{ directoryFromNfs3File(it)}
	}


	private NfsDirectory directoryFromNfs3File(Nfs3File file) {
		new NfsDirectory(
			name: file.name,
			provider: this,
			baseFile: file
		)
	}


}
