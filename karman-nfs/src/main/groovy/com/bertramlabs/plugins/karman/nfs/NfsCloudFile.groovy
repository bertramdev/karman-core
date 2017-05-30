package com.bertramlabs.plugins.karman.nfs

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.util.Mimetypes
import com.emc.ecs.nfsclient.nfs.io.Nfs3File
import com.emc.ecs.nfsclient.nfs.io.NfsFileInputStream
import com.emc.ecs.nfsclient.nfs.io.NfsFileOutputStream
import groovy.util.logging.Commons

/**
 * Wrapped implementation of a CloudFile for Karman leveraging the NFSv3 interfaces
 *
 * @author David Estes
 */
@Commons
class NfsCloudFile extends CloudFile{

	Nfs3File baseFile
	NfsDirectory parent
	InputStream sourceStream = null

	@Override
	InputStream getInputStream() {
		if(baseFile.exists()) {
			return new NfsFileInputStream(baseFile);
		}
		return null;
	}

	@Override
	void setInputStream(InputStream is) {
		sourceStream = is
	}

	@Override
	OutputStream getOutputStream() {
		if(!baseFile.exists()) {
			baseFile.createNewFile()
		}
		return new NfsFileOutputStream(baseFile)
	}

	@Override
	String getText(String encoding) {
		getInputStream()?.text
	}

	@Override
	byte[] getBytes() {
		getInputStream()?.bytes
	}

	@Override
	void setText(String text) {
		sourceStream = new ByteArrayInputStream(text.bytes)
	}

	@Override
	void setBytes(Object bytes) {
		sourceStream = new ByteArrayInputStream(bytes)
	}

	@Override
	Long getContentLength() {
		baseFile.length()
	}

	Boolean isDirectory() {
		return baseFile.isDirectory()
	}

	Boolean isFile() {
		return baseFile.isFile()
	}

	@Override
	String getContentType() {
		return Mimetypes.instance.getMimetype(name)
	}

	@Override
	void setContentType(String contentType) {
		// Content Type is not implemented in most file system stores
		return
	}

	@Override
	Boolean exists() {
		return baseFile.exists()
	}

	def save(acl = '') {
		// Auto saves
		if(sourceStream) {
			def os
			try {
				os = getOutputStream()
				os << sourceStream
			} finally {
				try {
					os.flush()
					os.close()
				} catch(ex) {}
				sourceStream.close()
			}
			sourceStream = null
		}
		return
	}

	@Override
	def delete() {
		baseFile.delete()
	}

	void setMetaAttribute(key, value) {
		log.warn("Karman CloudFile Meta Attributes Not Available for NfsCloudFile")
	}

	def getMetaAttribute(key) {
		log.warn("Karman CloudFile Meta Attributes Not Available for NfsCloudFile")
	}

	def getMetaAttributes() {
		log.warn("Karman CloudFile Meta Attributes Not Available for NfsCloudFile")
	}

	void removeMetaAttribute(key) {
		log.warn("Karman CloudFile Meta Attributes Not Available for NfsCloudFile")
	}

}
