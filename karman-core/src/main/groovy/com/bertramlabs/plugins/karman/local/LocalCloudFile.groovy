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
import groovy.util.logging.Slf4j
import java.io.ByteArrayInputStream;
import java.io.BufferedOutputStream;
import com.bertramlabs.plugins.karman.util.Mimetypes
import groovy.transform.CompileStatic

@Slf4j
class LocalCloudFile extends CloudFile<LocalDirectory> {
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

	@CompileStatic
	InputStream getInputStream() {
		fsFile.newInputStream()
	}

	@CompileStatic
	void setInputStream(InputStream inputS) {
		byte[] buffer = new byte[8192];
		int len;
		OutputStream out = new BufferedOutputStream(this.outputStream,1024*256)
		while ((len = inputS.read(buffer)) != -1) {
			if(len == 0) {
				sleep(50) //dont kill i/o uselessly when there is nothing yet available to be written
			} else {
				out.write(buffer, 0, len);	
			}
			
		}
		
		out.flush()
		out.close()
	}

	OutputStream getOutputStream() {
		if(!fsFile.exists()) {
			ensurePathExists()
			fsFile.createNewFile()
		}
		fsFile.newOutputStream()
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

	void setBytes(byte[] bytes) {
        ensurePathExists()
		fsFile.bytes = bytes
	}

	Long getContentLength() {
		fsFile.size()
	}

	Date getDateModified() {
		new Date(fsFile.lastModified())
	}

	String getContentType() {
		return Mimetypes.instance.getMimetype(name)
	}

	void setContentType(String contentType) {
		// Content Type is not implemented in most file system stores

	}

	Boolean exists() {
		fsFile.exists()
	}

    void setMetaAttribute(String key, String value) {
		log.warn("Karman CloudFile Meta Attributes Not Available for LocalCloudFile")
	}

	String getMetaAttribute(String key) {
		log.warn("Karman CloudFile Meta Attributes Not Available for LocalCloudFile")
		return null
	}

	Map<String,String> getMetaAttributes() {
		log.warn("Karman CloudFile Meta Attributes Not Available for LocalCloudFile")
		return null
	}

	void removeMetaAttribute(String key) {
		log.warn("Karman CloudFile Meta Attributes Not Available for LocalCloudFile")
	}

	void save(CloudFileACL acl) {
        // Auto saves
	}

	void delete() {
		fsFile.delete()
		cleanUpTree()
	}

	@Override
	Boolean isFile() {
		fsFile.isFile();
	}

	@Override
	Boolean isDirectory() {
		fsFile.isDirectory();
	}


	private cleanUpTree() {
		def parentDir = fsFile.parentFile
		while(parentDir.exists() && parentDir.canonicalPath != parent.fsFile.canonicalPath) {
			if(parentDir.list()?.size() == 0) {
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
