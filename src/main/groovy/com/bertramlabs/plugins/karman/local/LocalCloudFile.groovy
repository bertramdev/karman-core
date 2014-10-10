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
import com.bertramlabs.plugins.karman.util.Mimetypes

@Log4j
class LocalCloudFile extends CloudFile {
	LocalDirectory parent

	File getFsFile() {
		new File(parent.fsFile.path,name)
	}

	/**
	* Get URL
	* @param expirationDate
	* @return url
	*/
	URL getURL(Date expirationDate = null) {
		new URL("${provider.baseUrl}/${parent.name}/${name}")
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
        ensurePathExists()
		fsFile.text = text
	}

	void setBytes(bytes) {
        ensurePathExists()
		fsFile.bytes = bytes
	}

	Long getContentLength() {
		fsFile.size()
	}

	String getContentType() {
		return Mimetypes.instance.getMimetype(name)
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
		cleanUpTree()
	}

	private cleanUpTree() {
		def parentDir = fsFile.parentFile
		while(parentDir.canonicalPath != parent.fsFile.canonicalPath) {
			if(parentDir.list().size() == 0) {
				parentDir.delete()
				parentDir = parentDir.parentFile
			} else {
				break
			}
		}
	}

    private ensurePathExists() {
        def parentFile = fsFile.getParentFile()
        if(!parentFile.exists()) {
            parentFile.mkdirs()
        }
    }

}
