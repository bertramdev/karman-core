package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.CloudFileACL

class OpenstackSubDir extends OpenstackCloudFile {
	OpenstackDirectory parent

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

	void save(CloudFileACL acl) {
		throw new Exception("Not Implemented for a directory")
	}

	@Override
	void delete() {

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
	void removeMetaAttribute(Object key) {

	}


}
