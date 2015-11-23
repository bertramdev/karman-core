package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.CloudFile
import groovy.json.JsonSlurper
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Openstack Cloud File implementation for the Openstack Cloud Files API v1
 * @author David Estes
 */
class OpenstackCloudFile extends CloudFile {
	Map openstackMeta = [:]
	OpenstackDirectory parent
	private Boolean metaDataLoaded = false
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
		return existsFlag
	}



	/**
	 * Bytes setter/getter
	 */
	byte[] getBytes() {
		def result = inputStream?.bytes
		inputStream.close()
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
		if(valid) {
			OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider
			URI listUri
			URIBuilder uriBuilder = new URIBuilder("${openstackProvider.getEndpointUrl()}/${parent.name}/${name}".toString())


			HttpGet request = new HttpGet(uriBuilder.build())
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', openstackProvider.getToken()))
			HttpClient client = new DefaultHttpClient()
			HttpParams params = client.getParams()
			HttpConnectionParams.setConnectionTimeout(params, 30000)
			HttpConnectionParams.setSoTimeout(params, 20000)
			HttpResponse response = client.execute(request)

			openstackMeta = response.getAllHeaders()?.collectEntries() { Header header ->
				[(header.name): header.value]
			}

			metaDataLoaded = true
			HttpEntity entity = response.getEntity()
			return entity.content
		} else {
			return null
		}
	}

	/**
	 * Text setter/getter
	 * @param encoding
	 * @return text
	 */
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


	/**
	 * Save file
	 */
	def save(acl) {
		if (valid) {
			assert writeStream

			OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider
			URI listUri
			URIBuilder uriBuilder = new URIBuilder("${openstackProvider.getEndpointUrl()}/${parent.name}/${name}".toString())


			HttpPut request = new HttpPut(uriBuilder.build())
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', openstackProvider.getToken()))
			openstackMeta.each{entry ->
				if(entry.key != 'Content-Length') {
					request.addHeader(entry.key,entry.value.toString())
				}
			}
			request.setEntity(new InputStreamEntity(writeStream, this.getContentLength()))


			HttpClient client = new DefaultHttpClient()
			HttpParams params = client.getParams()
			HttpConnectionParams.setConnectionTimeout(params, 30000)
			HttpConnectionParams.setSoTimeout(params, 20000)

			HttpResponse response = client.execute(request)
			if(response.statusLine.statusCode != 201) {
				//Successfully Created File
				return false
			}

			metaDataLoaded = false
			openstackMeta = [:]

			existsFlag = true
			return true
		}
		return false
	}

	/**
	 * Delete file
	 */
	def delete() {
		if (valid) {
			s3Client.deleteObject(parent.name, name)
			existsFlag = false
		}
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
				String objectPath = openstackProvider.endpointUrl.split("/v1/")[1] + "/${parent.name}/${name}"
				objectPath = "/v1/" + objectPath
				String hmacBody = "GET\n${(expirationDate.time/1000).toLong()}\n${objectPath}".toString()
				SecretKeySpec key = new SecretKeySpec((openstackProvider.tempUrlKey).getBytes("UTF-8"), "HmacSHA1");
				Mac mac = Mac.getInstance("HmacSHA1");
				mac.init(key);
				String sig = mac.doFinal(hmacBody.getBytes('UTF-8')).encodeHex().toString()
				new URL("${openstackProvider.endpointUrl}/${parent.name}/${name}?temp_url_sig=${sig}&temp_url_expires=${(expirationDate.time/1000).toLong()}")
			} else {
				new URL("${openstackProvider.endpointUrl}/${parent.name}/${name}")
			}
		}
	}

	private void loadObjectMetaData() {
		if(valid) {
			OpenstackStorageProvider openstackProvider = (OpenstackStorageProvider) provider
			URI listUri
			URIBuilder uriBuilder = new URIBuilder("${openstackProvider.getEndpointUrl()}/${parent.name}/${name}".toString())


			HttpHead request = new HttpHead(uriBuilder.build())
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', openstackProvider.getToken()))
			HttpClient client = new DefaultHttpClient()
			HttpParams params = client.getParams()
			HttpConnectionParams.setConnectionTimeout(params, 30000)
			HttpConnectionParams.setSoTimeout(params, 20000)
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

	private boolean isValid() {
		assert parent
		assert parent.name
		assert name
		true
	}
}
