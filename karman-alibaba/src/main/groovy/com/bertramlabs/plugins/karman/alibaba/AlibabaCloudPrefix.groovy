package com.bertramlabs.plugins.karman.alibaba

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.CloudFileACL
import groovy.util.logging.Slf4j

@Slf4j
class AlibabaCloudPrefix extends AlibabaCloudFile {

	AlibabaDirectory parent

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
	String getText(String encoding = null) {
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
	void setBytes(byte[] bytes) {

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

	@Override
	void save(CloudFileACL acl) {
		throw new Exception("Not Implemented for a directory")
	}

	@Override
	void delete() {
		//
	}

	@Override
	void setMetaAttribute(String key, String value) {

	}

	@Override
	String getMetaAttribute(String key) {
		return null
	}

	@Override
	Map<String,String> getMetaAttributes() {
		return null
	}

	@Override
	void removeMetaAttribute(String key) {

	}
}
