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

/**
 * Azure Cloud File implementation for Azure Page Blob
 * @author Bob Whiton
 */
@Commons
class AzurePageBlobFile extends CloudFile {
	
	AzureDirectory parent

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
			def opts = [
				verb: 'GET',
				queryParams: [],
				path: "${parent.name}/${getEncodedName()}"
			]

			AzureStorageProvider azureProvider = (AzureStorageProvider) provider
			URIBuilder uriBuilder = new URIBuilder("${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString())
			
			HttpGet request = new HttpGet(uriBuilder.build())
			HttpClient client = azureProvider.prepareRequest(request, opts) 
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
		if (valid) {
			assert writeStream

			AzureStorageProvider azureProvider = (AzureStorageProvider) provider
			def contentLength = azureMeta['Content-Length'] // What we plan on saving

			// Make sure the directory exists
			def parentContainer = azureProvider[parent.name]
			if(!parentContainer.exists()) {
				parentContainer.save()
			}
			
			// First... must create the page blob (if it doesn't exist)
			if(!this.exists()) {
				URIBuilder uriBuilder = new URIBuilder("${azureProvider.getEndpointUrl()}${parent.name}/${getEncodedName()}".toString())
				HttpPut request = new HttpPut(uriBuilder.build())

				def createPageBlobOpts = [
					verb: 'PUT',
					queryParams: [:],
					headers: ['x-ms-blob-type': 'PageBlob', 'x-ms-blob-content-length': contentLength],
					path: "${parent.name}/${getEncodedName()}"
				]

				HttpClient client = azureProvider.prepareRequest(request, createPageBlobOpts) 
				HttpResponse response = client.execute(request)			
				if(response.statusLine.statusCode != 201) {
					HttpEntity responseEntity = response.getEntity()
					def xmlDoc = new XmlSlurper().parse(responseEntity.content)
					EntityUtils.consume(response.entity)
					def errMessage = "Error creating page blob ${parent.name}/${getEncodedName()}: ${xmlDoc.Message}"
					log.error errMessage
					log.error xmlDoc
					throw new Exception(errMessage)
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
		return false
	}

	private void uploadChunk(ChunkedInputStream chunkedStream, Long startByte, Long contentLength) {
		def opts = [
			verb: 'PUT',
			queryParams: [comp: 'page'],
			headers: [
				'x-ms-range':"bytes=${startByte}-${startByte+contentLength-1}",
				'Content-Length': contentLength,
				'x-ms-page-write':'update'
			],
			path: "${parent.name}/${getEncodedName()}"
		]
	
		AzureStorageProvider azureProvider = (AzureStorageProvider) provider
		URIBuilder uriBuilder = new URIBuilder("${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString())
		opts.queryParams?.each { k, v ->
			uriBuilder.addParameter(k, v)
		}
		HttpPut request = new HttpPut(uriBuilder.build())
		HttpClient client = azureProvider.prepareRequest(request, opts) 

		request.setEntity(new InputStreamEntity(chunkedStream, contentLength))
		HttpResponse response = client.execute(request)	
		if(response.statusLine.statusCode != 201) {
			HttpEntity responseEntity = response.getEntity()
			def xmlDoc = new XmlSlurper().parse(responseEntity.content)
			EntityUtils.consume(response.entity)

			def errMessage = "Error sending bytes to page blob ${parent.name}/${name} for startByte: ${startByte}, pageSize: ${contentLength}: ${xmlDoc.Message}"
			log.error errMessage
			log.error xmlDoc

			throw new Exception(errMessage)
		}
	}

	def delete() {
		def opts = [
			verb: 'DELETE',
			queryParams: [],
			path: "${parent.name}/${name}"
		]

		AzureStorageProvider azureProvider = (AzureStorageProvider) provider
		URIBuilder uriBuilder = new URIBuilder("${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString())
		
		HttpDelete request = new HttpDelete(uriBuilder.build())
		HttpClient client = azureProvider.prepareRequest(request, opts) 
		HttpResponse response = client.execute(request)
		if(response.statusLine.statusCode != 202) {
			HttpEntity responseEntity = response.getEntity()
			def xmlDoc = new XmlSlurper().parse(responseEntity.content)
			EntityUtils.consume(response.entity)

			def errMessage = "Error deleting page blob ${parent.name}/${name}: ${xmlDoc.Message}"
			log.error errMessage
			log.error xmlDoc

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
			AzureStorageProvider azureProvider = (AzureStorageProvider) provider
			
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
			log.info "signature: ${signature}"

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
			log.info "url generated: ${url}"

			return url
		}
	}

	private void loadObjectMetaData() {
		if(valid) {
			def opts = [
				verb: 'HEAD',
				queryParams: [],
				path: "${parent.name}/${getEncodedName()}"
			]

			AzureStorageProvider azureProvider = (AzureStorageProvider) provider
			URIBuilder uriBuilder = new URIBuilder("${azureProvider.getEndpointUrl()}/${parent.name}/${getEncodedName()}".toString())
			opts.queryParams?.each { k, v ->
				uriBuilder.addParameter(k, v)
			}

			HttpHead request = new HttpHead(uriBuilder.build())
			HttpClient client = azureProvider.prepareRequest(request, opts) 
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
			log.info "azureMeta: ${azureMeta}"
			EntityUtils.consume(response.entity)
			metaDataLoaded = true
		}
	}

	private String getEncodedName() {
		return java.net.URLEncoder.encode(name, "UTF-8").replaceAll('\\+', '%20')
	}

	private boolean isValid() {
		assert parent
		assert parent.name
		assert name
		true
	}
}
