package com.bertramlabs.plugins.karman.nfs

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.util.Mimetypes
import com.emc.ecs.nfsclient.nfs.io.Nfs3File
import com.emc.ecs.nfsclient.nfs.io.NfsFileInputStream
import com.emc.ecs.nfsclient.nfs.io.NfsFileOutputStream
import groovy.transform.CompileStatic
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
	String getText(String encoding=null) {
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
		if(!baseFile.exists()) {
			return false
		}
		return baseFile.isDirectory()
	}

	Boolean isFile() {
		if(!baseFile.exists()) {
			return false
		}
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
			try {
				if(!baseFile.parentFile.exists()) {
					baseFile.parentFile.mkdirs()
				}
			} catch(ex) {
				log.warn("Error ensuring path exists: ${baseFile.parentFile.path} - ${ex.message}...This may be ok though, moving on.")
			}


				copyStream(sourceStream, getOutputStream())
			sourceStream = null
		}
		return
	}

	@CompileStatic
	private copyStream(InputStream source, OutputStream out) {
		try {
			byte[] buffer = new byte[8192*2];
			int len;
			while ((len = source.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
		} finally {
			try {
				out.flush()
				out.close()
			} catch(ex) {}
			source.close()
		}
	}

	@Override
	def delete() {
		baseFile.delete()
		cleanUpTree()
	}

	private cleanUpTree() {
		Nfs3File parentDir =  baseFile.parentFile
		while(parentDir.absolutePath != parent.baseFile.absolutePath) {
			if(parentDir.list().size() == 0) {
				parentDir.delete()
				parentDir = parentDir.parentFile
			} else {
				break
			}
		}
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
