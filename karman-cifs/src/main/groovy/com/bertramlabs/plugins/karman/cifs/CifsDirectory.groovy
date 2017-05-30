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
import org.apache.tools.ant.DirectoryScanner
import java.nio.file.*
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile

class CifsDirectory extends com.bertramlabs.plugins.karman.Directory {

	String region

	File getCifsFile() {
		def rtn
		def cifsAuth = provider.getCifsAuthentication()
		if(cifsAuth)
			rtn = new SmbFile(smbUrl, name, cifsAuth)
		else
			rtn = new SmbFile(smbUrl, name)
		return rtn
	}

	Boolean exists() {
		def cifsFile = getCifsFile()
		return cifsFile ? cifsFile.exists() : false
	}

	List listFiles(options = [:]) {
		def rtn = []
		def baseDir = getCifsFile()
		recurseFiles(baseDir, null, rtn, options)
		return rtn
	}

	List recurseFiles(SmbFile file, String parentPath, List results, options = [:]) {
		def delimiter = options.delimiter ?: '/'
		FileSystem fileSystem = FileSystems.getDefault()
		def prefix
		def excludes = []
		def includes = []
		//setup options
		options.excludes?.each { exclude ->
			excludes << fileSystem.getPathMatcher(exclude)
		}
		options.includes?.each { include ->
			includes << fileSystem.getPathMatcher(include)	
		}
		if(options.prefix)
			prefix = fileSystem.getPathMatcher(option.prefix)
		//iterate files
		def fileList = file?.listFile()
		fileList?.each { fileRow ->
			def path = parentPath ? fileSystem.getPath(parentPath, fileRow.name) : "/"
			def addFile = true
			if(excludes?.size() > 0) {
				excludes?.each { exclude ->
					if(exclude.matches(path))
						addFile == false
				}
			}
			if(addFile == true && includes?.size() > 0) {
				addFile = false
				includes?.each { include ->
					if(include.matches(path)) {
						addFile == true
					}
				}
			}
			if(addFile == true && prefix) {
				addFile = false
				if(prefix.matches(path))
					addFile = true
			}
			if(addFile == true) {
				results << new CifsCloudFile(provider:provider, parent:this, name:fileRow.name)
				if(file.isDirectory()) {
					def newParent = parentPath + delimiter + file.name
					recurseFiles(fileRow, newParent, results, options)
				}
			}
		}
	}

	def save() {
		def baseDir = getCifsFile()
		baseDir.mkdirs()
	}

	CloudFile getFile(String name) {
		new CifsCloudFile(provider:provider, parent:this, name:name)
	}

}
