package com.bertramlabs.plugins.karman.azure

import com.bertramlabs.plugins.karman.CloudFile
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * Azure Cloud File implementation for Azure Page Blob
 * @author Bob Whiton
 */
@Commons
class AzurePageBlobFile extends CloudFile {
	
	AzureContainer parent

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

	Date getDateModified() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		if(azureMeta['Last-Modified']) {
			//java 8 only code here, awell
			return Date.from(LocalDateTime.parse(azureMeta['Last-Modified'], DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(ZoneOffset.UTC))
		}
		return null

	}
	
	void setContentLength(Long length) {
		if(length % 512 != 0) {
			throw new Exception('Content-Length must be divisible by 512')
		}
		setMetaAttribute('Content-Length', length)

	}

	String getContentType() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		azureMeta['Content-Type']
	}

	void setContentType(String contentType) {
		setMetaAttribute("Content-Type", contentType)
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
			AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

			def opts = [
				verb: 'GET',
				queryParams: [],
				path: "${parent.name}/${getEncodedName()}",
				uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}"
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

			AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider
			def contentLength = azureMeta['Content-Length'] // What we plan on saving
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

			// Make sure the directory exists
			def parentContainer = azureProvider[parent.name]
			if(!parentContainer.exists()) {
				parentContainer.save()
			}
			
			// First... must create the page blob (if it doesn't exist)
			if(!this.exists()) {
				def createPageBlobOpts = [
					verb: 'PUT',
					queryParams: [:],
					headers: ['x-ms-blob-type': 'PageBlob', 'x-ms-blob-content-length': contentLength],
					path: "${parent.name}/${getEncodedName()}",
					uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString()
				]

				def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(createPageBlobOpts) 
				HttpResponse response = client.execute(request)			
				if(response.statusLine.statusCode != 201) {
					azureProvider.throwResponseFailure(response, "Error creating page blob ${parent.name}/${getEncodedName()}")
				}
			}

			// Second.. chunk all the bytes
			def maxChunkSize = 4l * 1024l * 1024l
			long partSize = Math.min(maxChunkSize, contentLength)


			ChunkedInputStream chunkedStream = new ChunkedInputStream(writeStream, partSize)
			long filePosition = 0
			int partNumber = 1
			long startByte = 0
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
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		def opts = [
			verb: 'PUT',
			queryParams: [comp: 'page'],
			headers: [
				'x-ms-range':"bytes=${startByte}-${startByte+contentLength-1}",
				'Content-Length': contentLength,
				'x-ms-page-write':'update'
			],
			path: "${parent.name}/${getEncodedName()}",
			uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString()
		]
	
		def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(opts) 

		request.setEntity(new InputStreamEntity(chunkedStream, contentLength))
		HttpResponse response = client.execute(request)	
		if(response.statusLine.statusCode != 201) {
			azureProvider.throwResponseFailure(response, "Error sending bytes to page blob ${parent.name}/${name} for startByte: ${startByte}, pageSize: ${contentLength}")
		}
	}

	def delete() {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		def opts = [
			verb: 'DELETE',
			queryParams: [],
			headers: ['x-ms-delete-snapshots':'include'],
			path: "${parent.name}/${name}",
			uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}"
		]

		def (HttpClient client, HttpDelete request) = azureProvider.prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		if(response.statusLine.statusCode != 202) {
			try {
			azureProvider.throwResponseFailure(response, "Error deleting page blob")
			} catch(e) {}

			return false
		}

		return true
	}

	def copy(String srcURI, snapshot=null) {
		log.info "Copy: ${srcURI}, ${snapshot}"
		if (valid) {
			
			AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider
			
			// Make sure the directory exists
			def parentContainer = azureProvider[parent.name]
			if(!parentContainer.exists()) {
				parentContainer.save()
			}
			
			if(snapshot) {
				srcURI += "?snapshot=${snapshot}"
			}

			def copyPageBlobOpts = [
				verb: 'PUT',
				queryParams: [:],
				headers: ['x-ms-copy-source': srcURI],
				path: "${parent.name}/${getEncodedName()}",
				uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString()
			]

			def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(copyPageBlobOpts) 
			HttpResponse response = client.execute(request)			
			if(response.statusLine.statusCode != 202) {
				azureProvider.throwResponseFailure(response, "Error copying page blob")
			}

			metaDataLoaded = false
			azureMeta = [:]
			existsFlag = true

			def copyId = response.getHeaders('x-ms-copy-id').first().value 
			return copyId
		}
		return false
	}

	def snapshot() {
		log.info "snapshot started"
		if (valid) {
			AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider
			
			def snapshotBlobOpts = [
				verb: 'PUT',
				queryParams: [comp:'snapshot'],
				path: "${parent.name}/${getEncodedName()}",
				uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString()
			]

			def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(snapshotBlobOpts) 
			HttpResponse response = client.execute(request)			
			if(response.statusLine.statusCode != 201) {
				azureProvider.throwResponseFailure(response, "Error snapshotting page blob")
			}

			metaDataLoaded = false
			azureMeta = [:]
			existsFlag = true

			def snapshotDate = response.getHeaders('x-ms-snapshot').first().value 
			return snapshotDate
		}
		log.info "snapshot complete"
		return false
	}

	Boolean snapshotExists(String snapshotDate) {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider
		if(valid) {
			def opts = [
				verb: 'HEAD',
				queryParams: [snapshot: snapshotDate],
				path: "${parent.name}/${getEncodedName()}",
				uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString() 
			]

			def (HttpClient client, HttpHead request) = azureProvider.prepareRequest(opts) 
			HttpResponse response = client.execute(request)
			return (response.statusLine.statusCode == 200) 
		}
		return false
	}

	def deleteSnapshot(String snapshotDate) {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		def opts = [
			verb: 'DELETE',
			queryParams: [snapshot: snapshotDate],
			path: "${parent.name}/${name}",
			uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}"
		]

		def (HttpClient client, HttpDelete request) = azureProvider.prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		if(response.statusLine.statusCode != 202) {
			try {
			azureProvider.throwResponseFailure(response, "Error deleting page blob snapshot")
			} catch(e) {}

			return false
		}

		return true
	}

	def deleteSnapshots() {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		def opts = [
			verb: 'DELETE',
			headers: ['x-ms-delete-snapshots':'only'],
			path: "${parent.name}/${name}",
			uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}"
		]

		def (HttpClient client, HttpDelete request) = azureProvider.prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		if(response.statusLine.statusCode != 202) {
			try {
			azureProvider.throwResponseFailure(response, "Error deleting page blob snapshots")
			} catch(e) {}

			return false
		}

		return true
	}

	/**
	 * Get URL or pre-signed URL if expirationDate is set
	 * @param expirationDate
	 * @return url
	 */
	URL getURL(Date expirationDate = null) {
		if (valid) {
			AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider
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
					sr: 'b',
					sig: ''
				]

				// Generate the signature
				def signParams = [] 
				signParams << queryParams.sp
				signParams << ""
				signParams << queryParams.se
				signParams << "/blob/${azureProvider.storageAccount}/${parent.name}/${getEncodedName()}"
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
				URIBuilder uriBuilder = new URIBuilder("${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString())
				queryParams?.each { k, v ->
					uriBuilder.addParameter(k, v)
				}

				def url = uriBuilder.build().toURL()
				log.debug "url generated: ${url}"

				return url
			} else {
				return new URL("${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}")
			}
		}
	}

	private void loadObjectMetaData() {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		if(valid) {
			def opts = [
				verb: 'HEAD',
				queryParams: [],
				path: "${parent.name}/${getEncodedName()}",
				uri: "${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString() 
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

	private String getEncodedName() {
		return java.net.URLEncoder.encode(name, "UTF-8").replaceAll('\\+', '%20')
	}

	private boolean isValid() {
		if(!parent) {
			throw new Exception("No parent specified")
		}
		if(!parent.name) {
			throw new Exception("No parent name specified")
		}
		if(!name) {
			throw new Exception("No name specified")
		}
		true
	}
}
