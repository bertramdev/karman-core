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
import groovy.util.logging.Commons
import java.io.ByteArrayInputStream;
import com.bertramlabs.plugins.karman.util.Mimetypes
import groovy.transform.CompileStatic

@Commons
class CifsCloudFile extends CloudFile {

	CifsDirectory parent

	SmbFile getCifsFile() {
		def rtn
		def cifsAuth = provider.getCifsAuthentication()
		def parentFile = parent.getCifsFile()
		if(cifsAuth)
			rtn = new SmbFile(parentFile.path, name, cifsAuth)
		else
			rtn = new SmbFile(parentFile.path, name)
		return rtn
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
		getCifsFile().newInputStream()
	}

	@CompileStatic
	void setInputStream(InputStream inputS) {
		byte[] buffer = new byte[8192 * 2]
		int len
		OutputStream out = this.getOutputStream()
		while((len = inputS.read(buffer)) != -1) {
			out.write(buffer, 0, len);
		}
		out.flush()
		out.close()
	}

	OutputStream getOutputStream() {
		def cifsFile = getCifsFile()
		if(!cifsFile.exists()) {
			ensurePathExists()
			cifsFile.createNewFile()
		}
		cifsFile.newOutputStream()
	}

	String getText(String encoding = null) {
		def result = null
		if(encoding) {
			result = inputStream?.getText(encoding)
		} else {
			result = inputStream?.text
		}
		inputStream?.close()
		return result
	}

	byte[] getBytes() {
		def result = inputStream?.bytes
		inputStream?.close()
		return result
	}

	void setText(String text) {
    setBytes(text.bytes)
	}

	void setBytes(bytes) {
		ensurePathExists()
		def rawSourceStream = new ByteArrayInputStream(bytes)
		setInputStream(rawSourceStream)
	}

	Long getContentLength() {
		def cifsFile = getCifsFile()
		return cifsFile.size()
	}

	String getContentType() {
		return Mimetypes.instance.getMimetype(name)
	}

	void setContentType(String contentType) {
		// Content Type is not implemented in most file system stores
		return
	}

	Boolean exists() {
		def cifsFile = getCifsFile()
		return cifsFile.exists()
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
		def cifsFile = getCifsFile()
		cifsFile.delete()
		cleanUpTree()
	}

	@Override
	Boolean isFile() {
		def cifsFile = getCifsFile()
		return cifsFile.isFile()
	}

	@Override
	Boolean isDirectory() {
		def cifsFile = getCifsFile()
		return cifsFile.isDirectory()
	}

	private cleanUpTree() {
		def cifsFile = getCifsFile()
		def parentDir = cifsFile.parentFile
		while(parentDir.canonicalPath != parent.cifsFile.canonicalPath) {
			if(parentDir.list().size() == 0) {
				parentDir.delete()
				parentDir = parentDir.parentFile
			} else {
				break
			}
		}
	}

  private ensurePathExists() {
  	def cifsFile = getCifsFile()
  	def parentFile = cifsFile.getParentFile()
    if(!parentFile.exists()) {
    	parentFile.mkdirs()
    }
  }

}
