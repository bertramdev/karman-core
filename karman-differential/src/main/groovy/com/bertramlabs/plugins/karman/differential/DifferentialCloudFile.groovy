package com.bertramlabs.plugins.karman.differential

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.CloudFileACL
import com.bertramlabs.plugins.karman.CloudFileInterface
import com.bertramlabs.plugins.karman.StorageProvider
import com.bertramlabs.plugins.karman.util.Mimetypes
import groovy.transform.CompileStatic
import groovy.util.logging.Commons
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZ
import org.tukaani.xz.XZOutputStream

import java.util.zip.GZIPOutputStream


@Commons
public class DifferentialCloudFile extends CloudFile {
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
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			return new DifferentialInputStream(sourceFile, manifestFile.getInputStream())
		} else {
			return sourceFile.getInputStream()
		}
	}

	@Override
	Boolean isDirectory() {
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			return false
		}
		return sourceFile.isDirectory()
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
		setInputStream(new ByteArrayInputStream(text.bytes))
	}

	@Override
	void setBytes(bytes) {
		setInputStream(new ByteArrayInputStream(bytes))
	}

	@Override
	Long getContentLength() {
		CloudFile manifestFile = parent[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			DifferentialInputStream is = null
			try {
				is = new DifferentialInputStream(sourceFile, manifestFile.getInputStream())
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
			} finally {
				try {
					if(is != null) {
						is.close()
					}
				} catch(ignore) {
					//ignore
				}
			}
		} else {
			return sourceFile.getContentLength()
		}
	}

	Long getOnDeviceContentLength() {
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]

		if(manifestFile.exists()) {
			DifferentialInputStream is = null
			try {
				long contentLength = manifestFile.contentLength
				is = new DifferentialInputStream(sourceFile, manifestFile.getInputStream())
				ManifestData.BlockData currentBlock = is.getNextBlockData()
				while(currentBlock != null) {
//					contentLength += currentBlock.blockSize //this is the uncompressed size and is not accurate
					if(currentBlock.fileIndex == 0 && !currentBlock.zeroFilled) {
						String blockFilePath = ManifestData.BlockData.getBlockPath(sourceFile, currentBlock.block, 0, is.manifestData);
						CloudFile blockFile = parent.sourceDirectory[blockFilePath]
						contentLength += blockFile.contentLength
					}
					currentBlock = is.getNextBlockData()
				}
				return contentLength
			} finally {
				try {
					if(is != null) {
						is.close()
					}

				} catch(ignore) {
					//ignore
				}
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
		try {
			CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
			if(manifestFile.exists()) {
				return true
			}
		} catch(Exception e) {
			//ignore
		}

		return sourceFile.exists()
	}

	@Override
	@CompileStatic
	def save(acl) {
		CloudFileInterface manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		DifferentialInputStream diffInput = null
		if(!sourceFile.exists() || manifestFile.exists()) {
			try {
				if(manifestFile.exists())
					manifestFile.delete()
				//todo: cleanup all sub files since we are overwriting the file

				ManifestData manifestData = new ManifestData()
				manifestData.fileSize = internalContentLength
				manifestData.fileName = sourceFile.name
				manifestData.blockSize = ((DifferentialStorageProvider) provider).blockSize
				if(linkedFile != null) {
//				log.info("Linked File Detected: looking for manifest:" + linkedFile.name + "/karman.diff")
					CloudFileInterface linkedManifest = parent.sourceDirectory[linkedFile.name + "/karman.diff"]
					if(linkedManifest.exists()) {
//					log.info("Linked Manifest Found")
						diffInput = new DifferentialInputStream(linkedFile, linkedManifest.getInputStream())

						manifestData.sourceFiles = diffInput.manifestData.sourceFiles
						if(manifestData.sourceFiles == null) {
							manifestData.sourceFiles = []
						}
						manifestData.sourceFiles = [linkedFile.name] + manifestData.sourceFiles
					}

				}
				String headerString = manifestData.getHeader()
				Long calculateDiffSize = 0
				calculateDiffSize += headerString.getBytes().size()
				if(internalContentLength) {
					calculateDiffSize += ((long)((long)internalContentLength/(long)manifestData.blockSize)*44l)
					if(((long)internalContentLength%(long)manifestData.blockSize) > 0) {
						calculateDiffSize += 44l
					}
				}
				PipedOutputStream pos = new PipedOutputStream()
				PipedInputStream pis = new PipedInputStream(pos)
				def saveThread = Thread.start {
//				if(internalContentLength) {
//					manifestFile.setContentLength(calculateDiffSize)
//				}
					manifestFile.setInputStream(pis)
					manifestFile.save()
				}
				pos.write(headerString.getBytes())

				BlockDigestStream dataStream = new BlockDigestStream(rawSourceStream, pos, manifestData.blockSize, diffInput)
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
						GZIPOutputStream xz = new GZIPOutputStream(compressedBuffer)
						//XZOutputStream xz = new XZOutputStream(compressedBuffer, new LZMA2Options(0), XZ.CHECK_NONE)
						xz.write(buffer, 0, bytesRead)
						xz.finish()


						String blockFilePath = ManifestData.BlockData.getBlockPath(sourceFile, blockNumber, 0, manifestData);
						int attempts=0
						while(attempts < 5) {
							try {
								CloudFileInterface blockFile = parent.sourceDirectory[blockFilePath]
								byte[] compressedBufferArray = compressedBuffer.toByteArray()
								blockFile.setContentLength(compressedBufferArray.size())
								blockFile.setInputStream(new ByteArrayInputStream(compressedBufferArray));
								blockFile.save()
								break
							} catch(Exception e) {
								attempts++
								sleep(5000l*attempts + 5000l)

								if(attempts == 5) {
									log.error("Error saving block file...Max Attempts Reached...",e)
									throw new Exception("Error saving block file...Max Attempts Reached...",e)
								} else {
									log.error("Error saving block file...sleeping and trying again shortly...",e)
								}
							}
						}


					}
					blockNumber++
				}
				pos.flush()
				pos.close()
				saveThread.join()
			} finally {
				if(diffInput != null) {
					try {
						diffInput.close()
					} catch(Exception ignore) {

					}

				}
			}


		} else {
			sourceFile.save(acl)
		}
	}

	@Override
	def delete() {
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			parent.sourceDirectory.listFiles(prefix: sourceFile.name + "/", delimiter: "/")?.each { CloudFileInterface file ->
				file.delete()
			}
			if(sourceFile.exists()) {
				try {
					sourceFile.delete()
				} catch(Exception ex) {
					//trying to delete but could have recursively cleaned from the previous loop
					log.debug("Unable to delete root directory of differential file...this may have already been cleaned up from recursion...ignoring",ex)
				}

			}

		} else {
			if(sourceFile.exists()) {
				sourceFile.delete()
			}
		}

	}

	@Override
	void setMetaAttribute(key, value) {
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			manifestFile.setMetaAttribute(key, value)
		} else {
			sourceFile.setMetaAttribute(key, value)
		}
	}

	@Override
	String getMetaAttribute(key) {
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			return manifestFile.getMetaAttribute(key)
		} else {
			return sourceFile.getMetaAttribute(key)
		}
	}

	@Override
	Map getMetaAttributes() {
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
		if(manifestFile.exists()) {
			return manifestFile.getMetaAttributes()
		} else {
			return sourceFile.getMetaAttributes()
		}
	}

	@Override
	void removeMetaAttribute(key) {
		CloudFile manifestFile = parent.sourceDirectory[sourceFile.name + "/karman.diff"]
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
			DifferentialInputStream unflattenedStream = null
			PipedOutputStream pos
			PipedInputStream pis
			try {
					CloudFile originalManifest = parent.sourceDirectory[sourceFile.name + "/karman.diff2"]
					originalManifest.setInputStream(manifestFile.getInputStream())
					originalManifest.save()
					unflattenedStream = new DifferentialInputStream(sourceFile, originalManifest.getInputStream())
					pos = new PipedOutputStream()
					pis = new PipedInputStream(pos)
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
					pos = null //clear it for finally block unless exception occurs
					originalManifest.delete()

					//we gotta correct any children from this file based on the child list passed in
					if(children) {
						for(DifferentialCloudFile childrenFile in children) {
							DifferentialInputStream unflattenedChildStream = null
							PipedOutputStream childPos = null
							PipedInputStream childPis = null
							try {
								CloudFile childManifestFile = parent.sourceDirectory[childrenFile.name + "/karman.diff"]
								if(childManifestFile.exists()) {
									CloudFile originalChildManifest = parent.sourceDirectory[childrenFile.name + "/karman.diff2"]
									originalChildManifest.setInputStream(childManifestFile.getInputStream())
									originalChildManifest.save()
									unflattenedChildStream = new DifferentialInputStream(childrenFile.sourceFile, originalChildManifest.getInputStream())
									childPos = new PipedOutputStream()
									childPis = new PipedInputStream(childPos)
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


									originalChildManifest.delete()

								}
							} finally {
								if(childPos != null) {
									try {
										childPos.flush()
										childPos.close()
									} catch(ignore) {

									}
								}
								if(unflattenedStream != null) {
									try {
										unflattenedChildStream.close()
									} catch(ignore) {

									}
								}
							}

						}
					}

			} finally {
					try {
						unflattenedStream.close()
					} catch(ignore) {

					}
					try {
						if(pos != null) {
							pos.flush()
							pos.close()
						}
					} catch(ignore) {

					}
				}
		}
	}
}
