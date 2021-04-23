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

package com.bertramlabs.plugins.karman.google

import groovy.util.logging.Commons

import com.bertramlabs.plugins.karman.CloudFile

@Commons
class GoogleCloudDirectory extends CloudFile {

	GoogleCloudBucket parent

	@Override
	InputStream getInputStream() {
		return null
	}

	@Override
	void setInputStream(InputStream is) {

	}

	@Override
	OutputStream getOutputStream() {
		return null
	}

	@Override
	String getText(String encoding=null) {
		return null
	}


	@Override
	byte[] getBytes() {
		return new byte[0]
	}

	@Override
	void setText(String text) {

	}

	@Override
	void setBytes(Object bytes) {

	}

	@Override
	Long getContentLength() {
		return null
	}

	@Override
	String getContentType() {
		return null
	}

	@Override
	Date getDateModified() {
		return null
	}

	@Override
	void setContentType(String contentType) {

	}

	@Override
	Boolean exists() {
		return true
	}

	@Override
	Boolean isFile() {
		return false
	}

	@Override
	Boolean isDirectory() {
		return true
	}

	def save(acl) {
		throw new Exception("Not Implemented for a directory")
	}

	@Override
	def delete() {
		def dirName = name
		if(!name.endsWith('/')) {
			dirName = name + '/'
		}

		def path = "storage/v1/b/${parent.name}/o"
		def keepGoing = true
		GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider

		def requestOpts = [query: [
				bucket     : parent.name,
				prefix     : dirName,
				maxResults : 100,
				projection :'noAcl'
		]]

		while(keepGoing) {
			log.debug "grabbing objects from ${path}"
			def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'GET')
			log.debug "results on fetch ${results.data.items}"
			if(results.success && results.data.items?.size() > 0) {
				for(f in results.data.items) {
					log.debug "working on deleting ${f.name}"
					def deleteOpts = [additionalPathSegments: [f.name]]
					googleStorageProvider.callApi("https://storage.googleapis.com", path, deleteOpts, 'DELETE')
				}
			} else {
				keepGoing = false
			}
		}
		return true
	}

	@Override
	void setMetaAttribute(Object key, Object value) {

	}

	@Override
	def getMetaAttribute(Object key) {
		return null
	}

	@Override
	def getMetaAttributes() {
		return null
	}

	@Override
	void removeMetaAttribute(Object key) {

	}
}