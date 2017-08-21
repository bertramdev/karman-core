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

	CifsStorageProvider provider
	String region

	SmbFile getCifsFile() {
		def rtn
		def cifsAuth = provider.getCifsAuthentication()
		def dirName = name + '/'
		if(cifsAuth)
			rtn = new SmbFile(provider.getSmbUrl(dirName), cifsAuth)
		else
			rtn = new SmbFile(provider.getSmbUrl(dirName))
		return rtn
	}

	Boolean exists() {
		def cifsFile = getCifsFile()
		return cifsFile ? cifsFile.exists() : false
	}

	List listFiles(options = [:]) {
		Collection<CifsCloudFile> rtn = []

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
		prefix = options.prefix

		convertFilesToCloudFiles(baseFile, includes,excludes, prefix, rtn)

		for(int counter=0;counter < rtn.size(); counter++) {
			CifsCloudFile currentFile = rtn[counter]
			if(currentFile.isDirectory()) {
				convertFilesToCloudFiles(currentFile.baseFile, includes,excludes,prefix, rtn, counter+1)
			}
		}
		if(prefix) {
			rtn = rtn.findAll { file ->
				if(file.name.length() >= prefix.length()) {
					if(file.name.take(prefix.length()) == prefix) {
						return true
					}
				}
				return false
			}
		}

		return rtn
	}


	private void convertFilesToCloudFiles(SmbFile parentFile, includes, excludes,prefix, fileList, position=0) {
		Collection<CifsCloudFile> files = [];
		parentFile.listFiles()?.each { listFile ->
			def path = listFile.path.substring(baseFile.path.length())
			if(path.startsWith('/')) {
				path = path.substring(1)
			}
			if(isMatchedFile(path,includes,excludes,prefix)) {
				files << new CifsCloudFile(provider:provider, parent:this, name:path, baseFile: listFile)
			}
		}
		if(files) {
			fileList.addAll(position, files)
		}
	}

	private Boolean isMatchedFile(path, includes,excludes,prefix) {
		Boolean rtn = true
		if(excludes?.size() > 0) {
			excludes?.each { exclude ->
				if(exclude.matches(path))
					rtn = false
			}
		}
		if(includes?.size() > 0) {
			includes?.each { include ->
				if(include.matches(path)) {
					rtn = true
				}
			}
		}
		if(prefix) {
			if(path.length() >= prefix.length()) {
				if(path.take(prefix.length()) != prefix) {
					rtn = false
				}
			} else if(path.length() < prefix.length()) {
				if(prefix.take(path.length()) != path) {
					rtn = false
				}
			}
		}
		return rtn
	}


	def save() {
		def baseDir = getCifsFile()
		baseDir.mkdirs()
	}

	CloudFile getFile(String name) {
		new CifsCloudFile(provider:provider, parent:this, name:name)
	}

}
