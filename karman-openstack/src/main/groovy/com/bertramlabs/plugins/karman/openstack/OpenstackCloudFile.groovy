package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.CloudFile
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
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils
import com.bertramlabs.plugins.karman.util.ChunkedInputStream

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Openstack Cloud File implementation for the Openstack Cloud Files API v1
 * @author David Estes
 */
@Commons
class OpenstackCloudFile extends CloudFile {
	Map openstackMeta = [:]
	OpenstackDirectory parent
	private Boolean metaDataLoaded = false
	private Boolean chunked = false
	private Boolean existsFlag = null
	private InputStream writeStream

	/**
	 * Meta attributes setter/getter
	 */
	void setMetaAttribute(key, value) {
		openstackMeta[key] = value
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
		return openstackMeta[key]
	}

	Map getMetaAttributes() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		return openstackMeta
	}

	Date getDateModified() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}

		if(openstackMeta['Last-Modified']) {
			//java 8 only code here, awell
			return Date.from(LocalDateTime.parse(openstackMeta['Last-Modified'].toString(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(ZoneOffset.UTC))
		}
	}

	void removeMetaAttribute(key) {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		openstackMeta.remove(key)
	}

	/**
	 * Content length metadata
	 */
	Long getContentLength() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		openstackMeta['Content-Length']?.toLong()
	}

	/**
	 * Used to set the new content length for the data being uploaded
	 * @param length
	 */
	void setContentLength(Long length) {
		setMetaAttribute('Content-Length', length)
	}

	/**
	 * Fetches the content type of the file being requested
	 */
	String getContentType() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		openstackMeta['Content-Type']
	}

	/**
	 * Used to set the MimeType of the file being uploaded
	 * @param contentType the MimeType of the object
	 */
	void setContentType(String contentType) {
		setMetaAttribute("Content-Type", contentType)
	}


	/**
	 * Check if file exists
	 * @return true or false
	 */
	Boolean exists() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		log.debug("CloudFile exists? ${existsFlag}")
		return existsFlag
	}

	/**
	 * Bytes setter/getter
	 */
	byte[] getBytes() {
		def tmpInputStream = getInputStream()
		def result = tmpInputStream?.bytes
		tmpInputStream.close()
		return result
	}
	void setBytes(bytes) {
		writeStream = new ByteArrayInputStream(bytes)
		setContentLength(bytes.length)
	}

	/**
	 * Input stream getter
	 * @return inputStream
	 */
	InputStream getInputStream() {
		InputStream rtn = null
		if(valid) {
			OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider
			URI listUri
			URIBuilder uriBuilder = new URIBuilder("${openstackProvider.getEndpointUrl()}/${parent.name}/${encodedName}".toString())

			HttpGet request = new HttpGet(uriBuilder.build())
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', openstackProvider.getToken()))
			new OpenstackApiClient().withHttpClient(false) { HttpClient client ->
				HttpResponse response = client.execute(request)
				openstackMeta = response.getAllHeaders()?.collectEntries() { Header header ->
					[(header.name): header.value]
				}
				metaDataLoaded = true
				HttpEntity entity = response.getEntity()
				rtn = new BufferedInputStream(entity.content, 8000)
			}
		}

		return rtn
	}

	/**
	 * Text setter/getter
	 * @param encoding
	 * @return text
	 */
	String getText(String encoding = null) {
		def result
		InputStream tmpInputStream = getInputStream()
		if (encoding) {
			result = tmpInputStream?.getText(encoding)
		} else {
			result = tmpInputStream?.text
		}
		tmpInputStream?.close()
		return result
	}

	void setText(String text) {
		setBytes(text.bytes)
	}


	/**
	 * Save file
	 */
	def save(acl) {
		def rtn = false
		log.debug("Saving file ${encodedName}")
		if (valid) {
			assert writeStream

			OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider
			if(!this.getContentLength()) {
				log.debug("no content length, saving to temp file")
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
			// do chunk save
			if (openstackProvider.chunkSize > 0 && chunked) {
				return saveWithChunks(acl)
			}

			URI listUri
			URIBuilder uriBuilder = new URIBuilder("${openstackProvider.getEndpointUrl()}/${parent.name}/${encodedName}".toString())
			HttpPut request = new HttpPut(uriBuilder.build())
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', openstackProvider.getToken()))
			openstackMeta.each{entry ->
				if(entry.key != 'Content-Length') {
					request.addHeader(entry.key,entry.value.toString())
				}
			}
			request.setEntity(new InputStreamEntity(writeStream, this.getContentLength()))

			new OpenstackApiClient().withHttpClient() { HttpClient client ->
				HttpResponse response = client.execute(request)
				log.debug("save, response: ${response.statusLine.statusCode}")
				if(response.statusLine.statusCode != 201) {
					//failed to create file
					rtn = false
				}

				metaDataLoaded = false
				openstackMeta = [:]

				existsFlag = true
				rtn = true
			}
		}

		log.debug("save, rtn: ${rtn}")
		return rtn
	}

	def saveWithChunks(acl) {
		log.debug("saveWithChunks")
		def rtn = true
		OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider

		def segmentSize = openstackProvider.chunkSize
		def segment = 1
		def token = openstackProvider.token

		// first write the data parts
		// get connection for first part
		try {
			ChunkedInputStream cis = new ChunkedInputStream(writeStream, segmentSize)
			def keepGoing = true
			while (keepGoing) {
				HttpPut req = getObjectStoreConnection(token, openstackProvider, segment, openstackMeta)
				req.setEntity(new InputStreamEntity(cis, -1))

				new OpenstackApiClient().withHttpClient { HttpClient client ->
					HttpResponse response = client.execute(req)
					if(response.statusLine.statusCode != 201) {
						rtn = false
						keepGoing = false
					}
					segment++
					if(!cis.nextChunk())
						keepGoing = false
				}
			}
		} catch (Throwable t) {
			log.error(t)
			rtn = false
		} finally {
			writeStream.close()
		}

		// then write the metadata part
		def headers = ['X-Object-Manifest':"${parent.name}/${name}/"]
		try {
			log.debug("Writing manifest for ${name}")
			HttpPut req = getObjectStoreConnection(token, openstackProvider, null, headers)

			new OpenstackApiClient().withHttpClient { HttpClient client ->
				HttpResponse response = client.execute(req)
				if(response.statusLine.statusCode != 201) {
					rtn = false
				}
				metaDataLoaded = true
				openstackMeta = [:]

				existsFlag = true
			}
		}
		catch (Throwable t) {
			// log.error("Failed to write manifest data", t)
			rtn = false
		}

		return rtn
	}

	private HttpPut getObjectStoreConnection(String token, OpenstackStorageProvider provider, Integer segment, Map headers = [:]) {
		try {
			def part = segment ? "/part${segment.toString().padLeft(8, '0')}" : ''
			log.info("URL: ${provider.getEndpointUrl()}/${parent.name}/${encodedName}${part}")

			HttpPut request = new HttpPut("${provider.getEndpointUrl()}/${parent.name}/${encodedName}${part}")
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', token))
			headers.each{ entry ->
				if(entry.key != 'Content-Length') {
					request.addHeader(entry.key, entry.value.toString())
				}
			}
			return request 
		}
		catch (Throwable t) {
			log.error('Error building url connection to openstack object store', t)
			throw t
		}
	}

	/**
	 * Delete file
	 */
	def delete() {
		def rtn = true
		OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider
		URIBuilder uriBuilder = new URIBuilder("${openstackProvider.getEndpointUrl()}/${parent.name}/${encodedName}".toString())
		HttpDelete request = new HttpDelete(uriBuilder.build())
		request.addHeader("Accept", "application/json")
		request.addHeader(new BasicHeader('X-Auth-Token', openstackProvider.getToken()))
		openstackMeta.each{entry ->
			if(entry.key != 'Content-Length') {
				request.addHeader(entry.key,entry.value.toString())
			}
		}

		new OpenstackApiClient().withHttpClient { HttpClient client ->
			HttpResponse response = client.execute(request)
			if(response.statusLine.statusCode != 201) {
				rtn = false
			}
			existsFlag = false
		}

		return rtn
	}

	/**
	 * Get URL or pre-signed URL if expirationDate is set
	 * @param expirationDate
	 * @return url
	 */
	URL getURL(Date expirationDate = null) {
		if (valid) {
			OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider
			if (expirationDate) {
				String objectPath = openstackProvider.endpointUrl.split("/v1/")[1] + "/${parent.name}/${encodedName}"
				objectPath = "/v1/" + objectPath
				String hmacBody = "GET\n${(expirationDate.time/1000).toLong()}\n${objectPath}".toString()
				SecretKeySpec key = new SecretKeySpec((openstackProvider.tempUrlKey).getBytes("UTF-8"), "HmacSHA1");
				Mac mac = Mac.getInstance("HmacSHA1");
				mac.init(key);
				String sig = mac.doFinal(hmacBody.getBytes('UTF-8')).encodeHex().toString()
				new URL("${openstackProvider.endpointUrl}/${parent.name}/${encodedName}?temp_url_sig=${sig}&temp_url_expires=${(expirationDate.time/1000).toLong()}")
			} else {
				new URL("${openstackProvider.endpointUrl}/${parent.name}/${encodedName}")
			}
		}
	}

	private void loadObjectMetaData() {
		if(valid) {
			OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider
			URI listUri
			URIBuilder uriBuilder = new URIBuilder("${openstackProvider.getEndpointUrl()}/${parent.name}/${encodedName}".toString())


			HttpHead request = new HttpHead(uriBuilder.build())
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', openstackProvider.getToken()))

			new OpenstackApiClient().withHttpClient { HttpClient client ->
				HttpResponse response = client.execute(request)
				if(response.statusLine.statusCode == 404) {
					existsFlag = false
					return
				}
				existsFlag = true
				openstackMeta = response.getAllHeaders()?.collectEntries() { Header header ->
					[(header.name): header.value]
				}
				EntityUtils.consume(response.entity)
				metaDataLoaded = true
			}
		}
	}

	private boolean isValid() {
		assert parent
		assert parent.name
		assert name
		true
	}

	private String getEncodedName() {
		return java.net.URLEncoder.encode(name, "UTF-8").replaceAll('\\+', '%20')
	}
}
