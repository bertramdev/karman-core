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
import java.nio.file.*
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile

class CifsDirectory extends com.bertramlabs.plugins.karman.Directory {

	CifsStorageProvider provider
	String region

	SmbFile getCifsFile(String prefix=null) {
		def rtn
		def cifsAuth = provider.getCifsAuthentication()
		def dirName = name + '/'
		if(prefix) {
			dirName = dirName + normalizePath(prefix)
		}
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

		def delimiter = options.delimiter
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
		def baseFile = getCifsFile()
		if(options.prefix) {
			excludes << fileSystem.getPathMatcher("glob:*")
			excludes << fileSystem.getPathMatcher("glob:**/*")
			if(prefix.endsWith("/")) {
				baseFile = getCifsFile(prefix.substring(0,prefix.length() - 1))
				if(delimiter == '/') {
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
				} else {
					includes << fileSystem.getPathMatcher("glob:${prefix}**/*")
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
				}
			} else {
				if(delimiter == '/') {
					if(prefix.lastIndexOf('/') > 0) {
						baseFile = getCifsFile(prefix.substring(0,prefix.lastIndexOf('/')))
					}
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
					includes << fileSystem.getPathMatcher("glob:${prefix}*/**/*")
				} else {
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
				}
			}
		} else if (options.delimiter) {
			excludes << fileSystem.getPathMatcher("glob:*")
			excludes << fileSystem.getPathMatcher("glob:**/*")
			includes << fileSystem.getPathMatcher("glob:*")
		}


		convertFilesToCloudFiles(baseFile, includes,excludes,  rtn)

		if(delimiter != '/') {
			for(int counter=0;counter < rtn.size(); counter++) {
				CifsCloudFile currentFile = rtn[counter]
				if(currentFile.isDirectory()) {

					convertFilesToCloudFiles(currentFile.getCifsFile(), includes,excludes, rtn, counter+1)
				}
			}
		}

		rtn = rtn?.findAll {
			isMatchedFile(it.name,includes,excludes)
		}
//		if(prefix) {
//			rtn = rtn.findAll { file ->
//				if(file.name.length() >= prefix.length()) {
//					if(file.name.take(prefix.length()) == prefix) {
//						return true
//					}
//				}
//				return false
//			}
//		}

		return rtn
	}


	private void convertFilesToCloudFiles(SmbFile parentFile, includes, excludes, fileList, position=0) {
		Collection<CifsCloudFile> files = [];
		def baseFile = getCifsFile()
		parentFile.listFiles()?.each { listFile ->
			def path = listFile.path.substring(baseFile.path.length() - 1)
			if(path.startsWith('/')) {
				path = path.substring(1)
			}
			if(path.endsWith('/')) {
				path = path.substring(0,path.length() - 1)
			}
			if(isMatchedFile(path,includes,excludes)) {
				files << new CifsCloudFile(provider:provider, parent:this, name:path, baseFile: listFile)
			}
		}
		if(files) {
			fileList.addAll(position, files)
		}
	}

	private Boolean isMatchedFile(String stringPath, includes,excludes) {
		Path path = Paths.get(stringPath)
		Boolean rtn = true
		if(excludes?.size() > 0) {
			excludes?.each { exclude ->
				if(exclude.matches(path)) {
					rtn = false
				}

			}
		}
		if(includes?.size() > 0) {
			includes?.each { include ->
				if(include.matches(path)) {
					rtn = true
				}
			}
		}

		return rtn
	}


	def save() {
		def baseDir = getCifsFile()
		baseDir.mkdirs()
	}

	def delete() {
		def baseDir = getCifsFile()
		if(baseDir.exists()) {
			baseDir.delete()
		}
	}

	CloudFile getFile(String name) {
		name = name.replace('\\','/')
		new CifsCloudFile(provider:provider, parent:this, name:name)
	}

}
