package com.bertramlabs.plugins.karman.azure

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import groovy.json.JsonSlurper
import groovy.util.logging.Commons
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.InputStreamEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils
import com.bertramlabs.plugins.karman.util.ChunkedInputStream

import groovy.time.TimeCategory;
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Azure Cloud File implementation for Azure File
 * @author Bob Whiton
 */
@Commons
class AzureFile extends CloudFile {
	
	Directory parent // Not used

	String shareName

	Map azureMeta = [:]
	private InputStream writeStream
	private Boolean existsFlag = null
	private Boolean metaDataLoaded = false
	private Long chunkSize = 4096

	void setMetaAttribute(key, value) {
		azureMeta[key] = value
	}

	OutputStream getOutputStream() {
		def outputStream = new PipedOutputStream()
		writeStream = new PipedInputStream(outputStream)
		return outputStream
	}

	void setInputStream(InputStream inputS) {
		writeStream = inputS
	}


	String getMetaAttribute(key) {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		return azureMeta[key]
	}

	Map getMetaAttributes() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		return azureMeta
	}

	void removeMetaAttribute(key) {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		azureMeta.remove(key)
	}

	Long getContentLength() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		azureMeta['Content-Length']?.toLong()
	}
	
	void setContentLength(Long length) {
		setMetaAttribute('Content-Length', length)
	}

	String getContentType() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		azureMeta['x-ms-content-type'] ?: azureMeta['Content-Type']
	}

	void setContentType(String contentType) {
		setMetaAttribute("x-ms-content-type", contentType)
	}

	Boolean exists() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		return existsFlag
	}

	byte[] getBytes() {
		def result = inputStream?.bytes
		inputStream.close()
		return result
	}
	
	void setBytes(bytes) {
		writeStream = new ByteArrayInputStream(bytes)
		setContentLength(bytes.length)
	}

	InputStream getInputStream() {
		if(valid) {
			AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

			def opts = [
				verb: 'GET',
				queryParams: [],
				path: getFullPath(false),
				uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}"
			]

			def (HttpClient client, HttpGet request) = azureProvider.prepareRequest(opts) 
			HttpResponse response = client.execute(request)
			response.getAllHeaders()?.each { Header header ->
				if(header.name != 'x-ms-request-id') {
					azureMeta[header.name] = header.value
				}
			}
			metaDataLoaded = true
			HttpEntity entity = response.getEntity()
			return entity.content
		} else {
			return null
		}
	}

	String getText(String encoding = null) {
		def result
		if (encoding) {
			result = inputStream?.getText(encoding)
		} else {
			result = inputStream?.text
		}
		inputStream?.close()
		return result
	}

	void setText(String text) {
		setBytes(text.bytes)
	}

	def save(acl) {
		log.info "save started"
		if (valid) {
			assert writeStream

			AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider
			def contentLength = azureMeta['Content-Length'] // What we plan on saving

			if(!contentLength) {
				if(!this.getContentLength()) {
					File tmpFile = cacheStreamToFile(null,writeStream)
					this.setContentLength(tmpFile.size())
					InputStream is = tmpFile.newInputStream()
					try {
						this.setInputStream(tmpFile.newInputStream())
						return this.save(acl)
					} finally {
						if(is) {
							try { is.close()} catch(ex) {}
						}
						cleanupCacheStream(tmpFile)
					}
				}
			}
			// Make sure the directory exists
			ensurePathExists()
			
			// First... must create the file (if it doesn't exist)
			if(!this.exists()) {
				def createFileOpts = [
					verb: 'PUT',
					queryParams: [:],
					headers: ['x-ms-type': 'file', 'x-ms-content-length': contentLength],
					path: getFullPath(false),
					uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
				]

				addMSHeaders(createFileOpts, ['x-ms-content-type', 'x-ms-content-encoding', 'x-ms-content-language', 'x-ms-cache-control', 'x-ms-content-md5', 'x-ms-content-disposition'])

				def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(createFileOpts) 
				HttpResponse response = client.execute(request)			
				if(response.statusLine.statusCode != 201) {
					azureProvider.throwResponseFailure(response, "Error creating file ${getFullPath()}")
				}
			} else {
				// Make sure the size has not changed
				if(getContentLength() != contentLength) {
					def updateFileOpts = [
						verb: 'PUT',
						queryParams: [comp:'properties'],
						headers: ['x-ms-content-length': contentLength],
						path: getFullPath(false),
						uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
					]

					addMSHeaders(updateFileOpts, ['x-ms-content-type', 'x-ms-content-encoding', 'x-ms-content-language', 'x-ms-cache-control', 'x-ms-content-md5', 'x-ms-content-disposition'])

					def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(updateFileOpts) 
					HttpResponse response = client.execute(request)			
					if(response.statusLine.statusCode != 200) {
						azureProvider.throwResponseFailure(response, "Error updating file ${getFullPath()}")
					}
				}
			}

			// Second.. chunk all the bytes
			def maxChunkSize = 4l * 1024l * 1024l
			long partSize = Math.min(maxChunkSize, contentLength)


			ChunkedInputStream chunkedStream = new ChunkedInputStream(writeStream, partSize)
			long filePosition = 0
			int partNumber = 1
			int startByte = 0
			while(chunkedStream.available() >= 0 && (!contentLength || filePosition < contentLength)) {
				// Last part can be less than 5 MB. Adjust part size.
				
				partSize = Math.min(partSize, (contentLength - filePosition));

				uploadChunk(chunkedStream, startByte, partSize)

				filePosition += partSize
				partNumber++
				startByte += partSize
				chunkedStream.nextChunk()
			}

			metaDataLoaded = false
			azureMeta = [:]

			existsFlag = true
			return true
		}
		log.info "save complete"
		return false
	}

	private void uploadChunk(ChunkedInputStream chunkedStream, Long startByte, Long contentLength) {
		AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

		def opts = [
			verb: 'PUT',
			queryParams: [comp: 'range'],
			headers: [
				'x-ms-range':"bytes=${startByte}-${startByte+contentLength-1}",
				'Content-Length': contentLength,
				'x-ms-write':'update'
			],
			path: getFullPath(false),
			uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
		]
	
		def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(opts) 

		request.setEntity(new InputStreamEntity(chunkedStream, contentLength))
		HttpResponse response = client.execute(request)	
		if(response.statusLine.statusCode != 201) {
			azureProvider.throwResponseFailure(response, "Error sending bytes to file ${getFullPath()} for startByte: ${startByte}, pageSize: ${contentLength}")
		}
	}

	def delete() {
		AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

		def opts = [
			verb: 'DELETE',
			queryParams: [],
			path: getFullPath(false),
			uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}"
		]

		def (HttpClient client, HttpDelete request) = azureProvider.prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		if(response.statusLine.statusCode != 202) {
			try {
			azureProvider.throwResponseFailure(response, "Error deleting file")
			} catch(e) {}

			return false
		}

		return true
	}

	def copy(String srcURI) {
		log.info "Copy: ${srcURI}"
		if (valid) {
			
			AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider
			
			// Make sure the directory exists
			ensurePathExists()
			
			def copyFileOpts = [
				verb: 'PUT',
				queryParams: [:],
				headers: ['x-ms-copy-source': srcURI],
				path: getFullPath(false),
				uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
			]

			def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(copyFileOpts) 
			HttpResponse response = client.execute(request)			
			if(response.statusLine.statusCode != 202) {
				azureProvider.throwResponseFailure(response, "Error copying file")
			}

			metaDataLoaded = false
			azureMeta = [:]
			existsFlag = true

			def copyId = response.getHeaders('x-ms-copy-id').first().value 
			return copyId
		}
		return false
	}


	/**
	 * Get URL or pre-signed URL if expirationDate is set
	 * @param expirationDate
	 * @return url
	 */
	URL getURL(Date expirationDate = null) {
		if (valid) {
			AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider
			if(expirationDate) {
				// Calculate the start/end time
				if(!expirationDate) {
					def startDate = new Date()
					use (groovy.time.TimeCategory) {
					    expirationDate = startDate + 1.day
					}
				}

				def endDateFormat = expirationDate.format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))

				def queryParams = [
					sp: 'r',
					se: endDateFormat,
					sv: "2015-12-11",
					rscd: 'file; attachment',
					rsct: 'binary',
					sr: 'f',
					sig: ''
				]

				// Generate the signature
				def signParams = [] 
				signParams << queryParams.sp
				signParams << ""
				signParams << queryParams.se
				signParams << "/file/${azureProvider.storageAccount}/${getFullPath(false)}"
				signParams << ""
				signParams << ""
				signParams << ""
				signParams << queryParams.sv
				signParams << ""
				signParams << queryParams.rscd
				signParams << ""
				signParams << ""
				signParams << queryParams.rsct
				def signature = signParams.join('\n')
				log.debug "signature: ${signature}"

				// Sign and encode it
				Mac mac = Mac.getInstance("HmacSHA256")
				SecretKeySpec secretKeySpec = new SecretKeySpec(azureProvider.storageKey.toString().decodeBase64(), "HmacSHA256")
				mac.init(secretKeySpec)
				byte[] digest = mac.doFinal(signature.toString().getBytes('UTF-8'))
				def encodedSignature = digest.encodeBase64().toString()
				queryParams.sig = encodedSignature

				// Construct the URI
				URIBuilder uriBuilder = new URIBuilder("${azureProvider.getEndpointUrl()}/${getFullPath()}".toString())
				queryParams?.each { k, v ->
					uriBuilder.addParameter(k, v)
				}

				def url = uriBuilder.build().toURL()
				log.debug "url generated: ${url}"

				return url
			} else {
				return new URL("${azureProvider.getEndpointUrl()}/${getFullPath()}")
			}
		}
	}

	private void loadObjectMetaData() {
		AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

		if(valid) {
			def opts = [
				verb: 'HEAD',
				queryParams: [],
				path: getFullPath(false),
				uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString() 
			]

			def (HttpClient client, HttpHead request) = azureProvider.prepareRequest(opts) 
			HttpResponse response = client.execute(request)
			HttpEntity responseEntity = response.getEntity()
			
			if(response.statusLine.statusCode != 200) {
				existsFlag = false
				return
			}

			existsFlag = true
			azureMeta = [:]
			response.getAllHeaders()?.each { Header header ->
				if(header.name != 'x-ms-request-id') {
					azureMeta[header.name] = header.value
				}
			}
			EntityUtils.consume(response.entity)
			metaDataLoaded = true
		}
	}

	private getFullPath(encodedName = true) {
		if(encodedName) {
			def lastSlash = name.lastIndexOf('/')
			def encodedFileName
			def path = ''
			if(lastSlash > -1) {
				encodedFileName = java.net.URLEncoder.encode(name.substring(lastSlash + 1), "UTF-8").replaceAll('\\+', '%20')
				path = name.substring(0, lastSlash) + '/'
			} else {
				encodedFileName = java.net.URLEncoder.encode(name, "UTF-8").replaceAll('\\+', '%20')
			}
			return "${shareName}/${path}${encodedFileName}"
		} else {
			return "${shareName}/${name}"
		}
	}

	private ensurePathExists() {
		log.info "ensurePathExists: ${name}"
		AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

		// Structure of name is always '/subdir/paths'
		def pathParts = name.tokenize('/')
		
		def currentDirectory = azureProvider[shareName]
		if(!currentDirectory.exists()) {
			try {
				currentDirectory.save()	
			} catch(ex) {
				log.warn("Error ensuring path exists: ${shareName} - ${ex.message}...This may be ok though, moving on.")
			}
		}

		pathParts.eachWithIndex{ part, idx ->
			// Last part is the file name... ignore
			if(idx != pathParts.size() - 1) {
				try {
					currentDirectory = currentDirectory.getDirectory(part)
					if(!currentDirectory.exists()) {
						currentDirectory.save()
					}	
				} catch(ex) {
					// We should do a log warn here but keep going
					log.warn("Error ensuring path exists: ${part} - ${ex.message}...This may be ok though, moving on.")
				}
				
			}
		}	
	}

	private addMSHeaders(options, includeHeaderNames=[]){
		includeHeaderNames?.each { headerName ->
			if(azureMeta[headerName]) {
				options.headers[headerName] = azureMeta[headerName]
			}
		}	 	
	}

	private boolean isValid() {
		if(!shareName) {
			throw new Exception("No shareName specified")
		}
		if(!name) {
			throw new Exception("No name specified")
		}
		true
	}
}
