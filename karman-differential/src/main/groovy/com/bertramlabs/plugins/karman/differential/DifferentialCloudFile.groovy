package com.bertramlabs.plugins.karman.differential

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.CloudFileACL
import com.bertramlabs.plugins.karman.CloudFileInterface
import com.bertramlabs.plugins.karman.StorageProvider
import com.bertramlabs.plugins.karman.util.Mimetypes
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZ
import org.tukaani.xz.XZOutputStream


@Slf4j
public class DifferentialCloudFile extends CloudFile<DifferentialDirectory> {
	CloudFileInterface sourceFile
	DifferentialDirectory parent
	InputStream rawSourceStream = null
	private Long internalContentLength;
	private boolean internalContentLengthSet = false;
	private DifferentialCloudFile linkedFile = null

	DifferentialCloudFile(String name, DifferentialDirectory parent, CloudFileInterface sourceFile) {
		this.name = name
		this.provider = parent.provider
		this.parent = parent
		this.sourceFile = sourceFile
	}

	@Override
	@CompileStatic
	InputStream getInputStream() {
		if(sourceFile.isDirectory()) {
			CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
			if(manifestFile.exists()) {
				return new DifferentialInputStream(sourceFile, manifestFile.getInputStream())
			} else {
				return sourceFile.getInputStream()
			}

		} else {
			return sourceFile.getInputStream()
		}
	}

	@Override
	Boolean isDirectory() {
		if(sourceFile.isDirectory()) {
			CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
			return !manifestFile.exists()
		} else {
			return false
		}
	}

	@Override
	void setInputStream(InputStream is) {
		rawSourceStream = is
		//differential store...we gotta do something special here
	}

	@Override
	OutputStream getOutputStream() {
		return null
	}

	@Override
	String getText(String encoding) {
		return getInputStream().getText(encoding)
	}

	@Override
	String getText() {
		return getInputStream().text
	}


	@Override
	byte[] getBytes() {
		return getInputStream().bytes
	}

	@Override
	void setText(String text) {

	}

	@Override
	void setBytes(byte[] bytes) {

	}

	@Override
	Long getContentLength() {
		CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			DifferentialInputStream is = new DifferentialInputStream(sourceFile, manifestFile.getInputStream())
			if(is.manifestData.fileSize != null) {
				return is.manifestData.fileSize
			} else {
				long contentLength = 0
				ManifestData.BlockData currentBlock = is.getNextBlockData()
				while(currentBlock != null) {
					contentLength += currentBlock.blockSize
					currentBlock = is.getNextBlockData()
				}
				return contentLength
			}

		} else {
			return sourceFile.getContentLength()
		}
	}

	@Override
	String getContentType() {
		return Mimetypes.instance.getMimetype(name)
	}

	@Override
	Date getDateModified() {
		CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			return manifestFile.getDateModified()
		} else {
			return sourceFile.getDateModified()
		}
	}

	@Override
	void setContentType(String contentType) {
		// Content Type is not implemented in most file system stores
	}

	@Override
	void setContentLength(Long length) {
		internalContentLength = length
		internalContentLengthSet = true
		CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
		if(!manifestFile.exists()) {
			sourceFile.setContentLength(length)
		}

	}

	@Override
	Boolean exists() {
		return sourceFile.exists()
	}

	@Override
	@CompileStatic
	void save(CloudFileACL acl) {
		CloudFileInterface manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		if(!sourceFile.exists() || manifestFile.exists()) {

			manifestFile.delete()
			//todo: cleanup all sub files since we are overwriting the file
			PipedOutputStream pos = new PipedOutputStream()
			PipedInputStream pis = new PipedInputStream(pos)
			Thread.start {
				manifestFile.setInputStream(pis)
				manifestFile.save()
			}
			ManifestData manifestData = new ManifestData()
			manifestData.fileSize = internalContentLength
			manifestData.fileName = sourceFile.name
			manifestData.blockSize = ((DifferentialStorageProvider) provider).blockSize
			DifferentialInputStream diffInput = null
			if(linkedFile != null) {
				System.out.println("Linked File Detected!")
				diffInput = (DifferentialInputStream) linkedFile.getInputStream()
				manifestData.sourceFiles = diffInput.manifestData.sourceFiles
				if(manifestData.sourceFiles == null) {
					manifestData.sourceFiles = []
				}
				manifestData.sourceFiles = [linkedFile.name] + manifestData.sourceFiles
			}
			String headerString = manifestData.getHeader()
			pos.write(headerString.getBytes())

			InputStream dataStream = new BlockDigestStream(rawSourceStream, pos, manifestData.blockSize, diffInput)
			byte[] buffer = new byte[manifestData.blockSize]
			int bytesRead = 0
			long blockNumber = 0


			while((bytesRead = dataStream.read(buffer)) != -1) {
				//confirm the buffer is not full of zero byte arrays
				boolean allZero = true
				for(byte b : buffer) {
					if(b != 0) {
						allZero = false
						break
					}
				}
				if(!allZero && dataStream.lastBlockDifferent) {
					ByteArrayOutputStream compressedBuffer = new ByteArrayOutputStream()
					XZOutputStream xz = new XZOutputStream(compressedBuffer, new LZMA2Options(0), XZ.CHECK_NONE)
					xz.write(buffer, 0, bytesRead)
					xz.finish()


					String blockFilePath = ManifestData.BlockData.getBlockPath(sourceFile, blockNumber, 0, manifestData);
					CloudFileInterface blockFile = parent.sourceDirectory[blockFilePath]
					blockFile.setInputStream(new ByteArrayInputStream(compressedBuffer.toByteArray()));
					blockFile.save()

				}
				blockNumber++
			}
			pos.flush()
			pos.close()

		} else {
			sourceFile.save(acl)
		}
	}

	@Override
	void delete() {
		sourceFile.delete()
	}

	@Override
	void setMetaAttribute(String key, String value) {
		CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			manifestFile.setMetaAttribute(key, value)
		} else {
			sourceFile.setMetaAttribute(key, value)
		}
	}

	@Override
	String getMetaAttribute(String key) {
		CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			return manifestFile.getMetaAttribute(key)
		} else {
			return sourceFile.getMetaAttribute(key)
		}
	}

	@Override
	Map<String, String> getMetaAttributes() {
		CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			return manifestFile.getMetaAttributes()
		} else {
			return sourceFile.getMetaAttributes()
		}
	}

	@Override
	void removeMetaAttribute(String key) {
		CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			manifestFile.removeMetaAttribute(key)
		} else {
			sourceFile.removeMetaAttribute(key)
		}
	}

	void setLinkedFile(DifferentialCloudFile linkedFile) {
		this.linkedFile = linkedFile
	}

	void flatten(List<DifferentialCloudFile> children = null) {
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		List<String> sourceFilesToUnlink = []
		//only do this if it is indeed a differential file
		if(manifestFile.exists()) {
			CloudFile originalManifest = parent.sourceDirectory[sourceFile.name + "/karman.diff2"]
			originalManifest.setInputStream(manifestFile.getInputStream())
			originalManifest.save()
			DifferentialInputStream unflattenedStream = new DifferentialInputStream(sourceFile, originalManifest.getInputStream())
			PipedOutputStream pos = new PipedOutputStream()
			PipedInputStream pis = new PipedInputStream(pos)
			Thread.start {
				manifestFile.setInputStream(pis)
				manifestFile.save()
			}

			ManifestData manifestData = new ManifestData()
			manifestData.fileSize = unflattenedStream.manifestData.fileSize
			manifestData.fileName = unflattenedStream.manifestData.fileName
			manifestData.blockSize = unflattenedStream.manifestData.blockSize
			sourceFilesToUnlink = unflattenedStream.manifestData.sourceFiles
			manifestData.sourceFiles = null //clear source files

			String headerString = manifestData.getHeader()
			pos.write(headerString.getBytes())

			ManifestData.BlockData currentBlock = unflattenedStream.getNextBlockData()
			while(currentBlock != null) {
				if(currentBlock.fileIndex != 0) {
					if(!currentBlock.zeroFilled) {
						//block file index is not 0 so we need to grab it and pull it in
						String originalBlockFilePath = ManifestData.BlockData.getBlockPath(sourceFile, currentBlock.block, currentBlock.fileIndex, unflattenedStream.manifestData)
						String newBlockFilePath = ManifestData.BlockData.getBlockPath(sourceFile, currentBlock.block, 0, manifestData)

						CloudFileInterface blockFile = parent.sourceDirectory[originalBlockFilePath]
						CloudFileInterface destBlockFile = parent.sourceDirectory[newBlockFilePath]
						destBlockFile.setInputStream(blockFile.getInputStream())
						destBlockFile.save()
					}
					currentBlock.fileIndex = 0
				}
				pos.write(currentBlock.generateBytes())
				currentBlock = unflattenedStream.getNextBlockData()
			}
			pos.flush()
			pos.close()
			unflattenedStream.close()
			originalManifest.delete()

			//we gotta correct any children from this file based on the child list passed in
			if(children) {
				for(DifferentialCloudFile childrenFile in children) {
					CloudFile childManifestFile = parent.sourceDirectory[childrenFile.name + "/karman.diff"]
					if(childManifestFile.exists()) {
						CloudFile originalChildManifest = parent.sourceDirectory[childrenFile.name + "/karman.diff2"]
						originalChildManifest.setInputStream(childManifestFile.getInputStream())
						originalChildManifest.save()
						DifferentialInputStream unflattenedChildStream = new DifferentialInputStream(childrenFile.sourceFile, originalChildManifest.getInputStream())
						PipedOutputStream childPos = new PipedOutputStream()
						PipedInputStream childPis = new PipedInputStream(childPos)
						Thread.start {
							childManifestFile.setInputStream(childPis)
							childManifestFile.save()
						}

						ManifestData childManifestData = new ManifestData()
						childManifestData.fileSize = unflattenedChildStream.manifestData.fileSize
						childManifestData.fileName = unflattenedChildStream.manifestData.fileName
						childManifestData.blockSize = unflattenedChildStream.manifestData.blockSize
						childManifestData.sourceFiles = unflattenedChildStream.manifestData.sourceFiles
						def fileIndicesToUnlink = []

						if(sourceFilesToUnlink) {
							sourceFilesToUnlink.each { sourceFileToUnlink ->
								Integer idx = childManifestData.sourceFiles?.indexOf(sourceFileToUnlink)
								if(idx != null && idx >= 0) {
									fileIndicesToUnlink << idx + 1
								}
							}
							childManifestData.sourceFiles?.removeAll(sourceFilesToUnlink)

						}
						int targetIndex = childManifestData.sourceFiles.indexOf(sourceFile.name) + 1
						String childHeaderString = childManifestData.getHeader()
						childPos.write(childHeaderString.getBytes())
						ManifestData.BlockData childCurrentBlock = unflattenedChildStream.getNextBlockData()
						while(childCurrentBlock != null) {
							if(fileIndicesToUnlink.contains(childCurrentBlock.fileIndex)) {
								childCurrentBlock.fileIndex = targetIndex
							}
							childPos.write(childCurrentBlock.generateBytes())
							childCurrentBlock = unflattenedChildStream.getNextBlockData()
						}
						childPos.flush()
						childPos.close()
						unflattenedChildStream.close()
						originalChildManifest.delete()

					}
				}
			}
		}
	}
}
