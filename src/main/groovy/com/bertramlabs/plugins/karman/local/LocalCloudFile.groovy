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
import groovy.util.logging.Log4j
import java.io.ByteArrayInputStream;

@Log4j
class LocalCloudFile extends CloudFile {
	LocalDirectory parent

	File getFsFile() {
		new File(parent.fsFile.path,name)
	}


	InputStream getInputStream() {
		fsFile.newDataInputStream()
	}

	String getText(String encoding=null) {
		def result = null
		if(encoding) {
			fsFile.getText(encoding)
		} else {
			fsFile.text
		}
	}

	byte[] getBytes() {
		fsFile.bytes
	}

	void setText(String text) {
		fsFile.text = text
	}

	void setBytes(bytes) {
		fsFile.bytes = bytes
	}

	Long getContentLength() {
		fsFile.size()
	}

	String getContentType() {
		def servletContext = org.codehaus.groovy.grails.web.context.ServletContextHolder.getServletContext()
		return servletContext ? servletContext.getMimeType(name) : java.net.URLConnection.guessContentTypeFromName(name)
	}

	void setContentType(String contentType) {
		// Content Type is not implemented in most file system stores
		return
	}

	Boolean exists() {
		fsFile.exists()
	}

    void setMetaAttribute(key, value) {
		log.warn("Karman CloudFile Meta Attributes Not Available for LocalCloudFile")
	}

	def getMetaAttribute(key) {
		log.warn("Karman CloudFile Meta Attributes Not Available for LocalCloudFile")
	}

	def getMetaAttributes() {
		log.warn("Karman CloudFile Meta Attributes Not Available for LocalCloudFile")
	}

	void removeMetaAttribute(key) {
		log.warn("Karman CloudFile Meta Attributes Not Available for LocalCloudFile")
	}

    def save(acl = '') {
        // Auto saves
		return
	}

	def delete() {
		fsFile.delete()
	}

}