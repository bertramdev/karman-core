package com.bertramlabs.plugins.karman.alibaba

import com.bertramlabs.plugins.karman.CloudFile
import groovy.util.logging.Commons

@Commons
class AlibabaCloudPrefix extends CloudFile {

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
	String getText(String encoding) {
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
		return null
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
