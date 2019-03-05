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

package com.bertramlabs.plugins.karman.local

import com.bertramlabs.plugins.karman.*

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.FileSystem

class LocalDirectory extends com.bertramlabs.plugins.karman.Directory {

	String region

	File getFsFile() {
		new File(provider.basePath, name)
	}

	Boolean exists() {
		fsFile.exists()
	}

	List listFiles(options = [:]) {
		Collection<LocalCloudFile> rtn = []

		def delimiter = options.delimiter
		FileSystem fileSystem = FileSystems.getDefault()
		String prefix
		def excludes = []
		def includes = []
		//setup options
		options.excludes?.each { exclude ->
			excludes << fileSystem.getPathMatcher(exclude)
		}
		options.includes?.each { include ->
			includes << fileSystem.getPathMatcher(include)
		}
		File rootFolder = getFsFile()
		prefix = options.prefix as String

		if(delimiter == '/' && prefix) {
			prefix = normalizePath(prefix)
		}

		if(options.prefix) {
			excludes << fileSystem.getPathMatcher("glob:*")
			excludes << fileSystem.getPathMatcher("glob:**/*")
			if(prefix.endsWith("/")) {
				rootFolder = new File(fsFile, prefix.substring(0,prefix.length() - 1))
				if(delimiter == '/') {
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
				} else {
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
					includes << fileSystem.getPathMatcher("glob:${prefix}**/*")
				}
			} else {
				if(delimiter == '/') {
					if(prefix.lastIndexOf('/') > 0) {
						rootFolder = new File(fsFile, prefix.substring(0,prefix.lastIndexOf('/')))
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
		convertFilesToCloudFiles(rootFolder, includes,excludes, rtn)

		if(delimiter != '/') {
			for(int counter = 0; counter < rtn.size(); counter++) {
				LocalCloudFile currentFile = rtn[counter]
				if(currentFile.isDirectory()) {
					convertFilesToCloudFiles(currentFile.fsFile, includes, excludes, rtn, counter + 1)
				}
			}
		}
		rtn = rtn?.findAll {
			isMatchedFile(it.name,includes,excludes)
		}?.sort{ a, b ->  a.isFile() <=> b.isFile() ?: a.name <=> b.name}

		return rtn
	}

	private void convertFilesToCloudFiles(File parentFile, includes, excludes, fileList, position=0) {
		Collection<LocalCloudFile> files = [];
		parentFile.listFiles()?.each { listFile ->
			def path = listFile.path.substring(fsFile.path.length())
			if(path.startsWith('/')) {
				path = path.substring(1)
			}
			if(isMatchedFile(path,includes,excludes)) {
				files << new LocalCloudFile(provider: provider, parent: this, name: path)
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
		fsFile.mkdirs()
	}

	def delete() {
		if(fsFile.exists()) {
			fsFile.deleteDir()
		}
	}

	CloudFile getFile(String name) {
		new LocalCloudFile(provider: provider, parent: this, name: name)
	}

}