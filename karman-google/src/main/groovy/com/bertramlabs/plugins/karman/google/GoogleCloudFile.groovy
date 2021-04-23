/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bertramlabs.plugins.karman.google

import groovy.util.logging.Commons

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.util.ChunkedInputStream

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.lang.reflect.InvocationTargetException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Commons
class GoogleCloudFile extends CloudFile {

	GoogleCloudBucket parent

	private Long internalContentLength = null
	private Boolean internalContentLengthSet =false
	private InputStream writeStream
	private Boolean chunked = false
	private Boolean metaDataLoaded = false
	private Boolean existsFlag = null

	// In google, metadata is purely just metadata but in karman, most things are metadata
	// So, if properties are set on metadata that should be on the object resource, do NOT set them on
	// metadata when saving (but load them as metadata on loading)
	Map googleMeta = [:]
	private Map metadataToResourceMapping = [
		generation:  [:],
		metageneration: [:],
		contentType: [metaDataKey: 'Content-Type', writeable: true],
		timeCreated: [:],
		updated: [metaDataKey: 'Last-Modified'],
		customTime: [writeable: true],
		timeDeleted: [:],
		temporaryHold: [writeable: true],
		eventBasedHold: [writeable: true],
		retentionExpirationTime: [:],
		storageClass: [writeable: true],
		timeStorageClassUpdated: [:],
		size: [metaDataKey: 'Content-Length'],
		md5Hash: [writeable: true],
		mediaLink: [:],
		contentEncoding: [metaDataKey: 'Content-Encoding', writeable: true],
		contentDisposition: [metaDataKey: 'Content-Disposition', writeable: true],
		contentLanguage: [metaDataKey: 'Content-Language', writeable: true],
		cacheControl: [writeable: true],
		crc32c: [writeable: true],
		componentCount: [:],
		etag: [:],
		kmsKeyName: [:]
	]

	void setMetaAttribute(key, value) {
		log.trace "setMetaAttribute key: ${key}, value: ${value}"
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		googleMeta[getStoreMetadataKey(key)] = value
	}

	String getMetaAttribute(key) {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		log.debug "googleMeta: ${googleMeta}"
		return googleMeta[getStoreMetadataKey(key)]
	}

	Map getMetaAttributes() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		googleMeta
	}

	void removeMetaAttribute(key) {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		googleMeta.remove(key)
		googleMeta.remove(getStoreMetadataKey(key))
	}

	Long getContentLength() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		if(internalContentLengthSet || !exists()) {
			return internalContentLength
		}
		googleMeta['Content-Length']?.toLong()
	}

	Date getDateModified() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		if(!exists()) {return null}
		return googleMeta['Last-Modified']
	}

	void setContentLength(Long length) {
		setMetaAttribute('Content-Length', length)
		internalContentLength = length
		internalContentLengthSet = true
	}

	String getContentType() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		googleMeta['Content-Type']
	}

	void setContentType(String contentType) {
		googleMeta["Content-Type"] = contentType
	}

	OutputStream getOutputStream() {
		def outputStream = new PipedOutputStream()
		writeStream = new PipedInputStream(outputStream)
		return outputStream
	}

	void setInputStream(InputStream inputS) {
		writeStream = inputS
	}

	byte[] getBytes() {
		def result = inputStream?.bytes
		inputStream?.close()
		return result
	}

	void setBytes(bytes) {
		writeStream = new ByteArrayInputStream(bytes)
		setContentLength(bytes.length)
	}

	InputStream getInputStream() {
		if(valid) {
			GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider
			def path = "storage/v1/b/${parent.name}/o/${encodedName}"
			def requestOpts = [inputStream: true, query: [alt:'media']]
			def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'GET')
			return results.content
		} else {
			return null
		}
	}

	String getText(String encoding = null) {
		def result
		if(encoding) {
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

	URL getURL(Date expirationDate = null) {
		if(valid) {
			return new URL("https://storage.googleapis.com/storage/v1/b/${parent.name}/o/${encodedName}")
		}
	}

	Boolean exists() {
		if(valid) {
			if(!metaDataLoaded) {
				loadObjectMetaData()
			}
			return existsFlag
		} else {
			false
		}
	}

	def save(acl) {
		log.debug "save"
		if(valid) {
			if(!writeStream) {
				return update(acl)
			}

			assert writeStream

			GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider

			// First.. kick off the resumable upload
			def path = "upload/storage/v1/b/${parent.name}/o"
			def requestOpts = [
					headers: [:],
					query: [
					    name: name,
						uploadType: 'resumable'
					],
					body: [metadata: [:]]
			]
			// Add all the properties (stored in metadata) as the payload
			addMetadataToPayload(requestOpts)

			Long contentLength = (internalContentLengthSet || !exists()) ? internalContentLength : getContentLength()
			log.debug "upload path ${path} with requestOpts ${requestOpts}"
			def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'POST')
			if(!results.success) {
				return false
			}

			// Second.. upload the actual data
			def mapParams = parseQueryParams(results.location)
			requestOpts = [
				headers          : [
					'Content-Type': googleMeta['Content-Type'] ?: 'application/octet-stream'
				],
				location         : results.location,
				query            : mapParams,
				timeout          : 30000,
				connectionTimeout: 30000,
				body             : [
					type       : 'inputStream',
					inputStream: writeStream
				]
			]

			log.debug "upload file contents to ${path} with query params ${requestOpts.query}"
			if (googleStorageProvider.chunkSize > 0 && chunked) {
				log.debug "performing chunk upload"
				try {
					def maxChunkSize = 16l * 1024l * 1024l // 16MB
					long partSize = Math.min(maxChunkSize, contentLength)
					ChunkedInputStream chunkedStream = new ChunkedInputStream(writeStream, partSize)
					long startByte = 0

					requestOpts.chunked = true
					requestOpts.body.inputStream = chunkedStream // swap out the inputstream for the chunkedInputStream

					while(chunkedStream.available() >= 0 && (!contentLength || startByte < contentLength)) {
						partSize = Math.min(partSize, (contentLength - startByte))

						def contentRange = "bytes ${startByte}-${startByte + partSize - 1}/${contentLength}"
						requestOpts.headers['Content-Range'] = contentRange
						log.debug "Calling upload with Content-Range:${contentRange}"
						results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'PUT')
						log.debug "Calling upload results Content-Range ${contentRange}: $results"
						startByte += partSize
						chunkedStream.nextChunk()
					}
				}
				catch (e) {
					log.error "Error on upload: ${e}", e
					return false
				}
				finally {
					writeStream.close()
				}
			} else {
				results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'PUT')
				if (!results.success) {
					return false
				}
			}

			metaDataLoaded = false
			googleMeta = [:]
			existsFlag = true
			return true
		}
	}

	private update(acl) {
		log.debug "update: ${name} ${acl}"
		def success = false
		if(valid) {
			GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider
			if(exists()) {
				def path = "storage/v1/b/${parent.name}/o"
				def requestOpts = [additionalPathSegments: [name], body : [metadata: [:]]]
				addMetadataToPayload(requestOpts)
				log.debug "calling update to ${path}/${name} with ${requestOpts}"
				def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'PATCH')
				success = results.success

				metaDataLoaded = false
				googleMeta = [:]
				existsFlag = true
			} else {
				log.warn "Attempting an update for a file that doesn't exist ${name}"
			}
		}
		success
	}


	/**
	 * Delete file
	 */
	def delete() {
		def result = false
		if(valid) {
			GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider
			def path = "storage/v1/b/${parent.name}/o/${encodedName}"
			def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, [:], 'DELETE')
			if(results.success) {
				existsFlag = false
				result = true
			} else {
				log.error "Error in deleting file: ${name}: ${results}"
			}
		}
		result
	}

	private addMetadataToPayload(requestOpts) {
		log.debug "addMetadataToPayload"
		// Add all the properties (stored in metadata) as the payload
		googleMeta.each { k, v ->
			def (propKey, writeable) = getPropForMetadataKey(k)
			if (propKey) {
				if (writeable && !(propKey.toLowerCase() in ['crc32c','md5hash'])) { // Can't update the checksum
					requestOpts.body[propKey] = v
				}
			} else {
				requestOpts.body.metadata[k] = v
			}
		}
	}

	private void loadObjectMetaData() {
		log.debug "loadObjectMetaData ${name}"
		if(valid) {
			metaDataLoaded = true

			GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider
			def path = "storage/v1/b/${parent.name}/o"
			def requestOpts = [additionalPathSegments: [name]]
			def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'GET')
			if(results.statusCode == 404) {
				existsFlag = false
				return
			}
			existsFlag = true
			// put it all in metadata to conform to other karman provider expectations
			metadataToResourceMapping.each {resourcePropName, v ->
				def propValue = results.data[resourcePropName]
				if(propValue) {
					setMetaAttribute(resourcePropName, propValue)
				}
			}

			results.data['metadata']?.each { k, v ->
				setMetaAttribute(k, v)
			}
		}
	}

	private getStoreMetadataKey(key) {
		// We map some properties (like size) to Content-Length
		def storeKey = key
		if(metadataToResourceMapping[key]?.metaDataKey) {
			storeKey = metadataToResourceMapping[key].metaDataKey
		}
		storeKey
	}

	private getPropForMetadataKey(key) {
		def propKey
		def writable = true
		// The map back to the object property from a metadata key
		propKey = metadataToResourceMapping.find { it ->
			it.value.metaDataKey == key
		}?.getKey()
		if(!propKey) {
			propKey = metadataToResourceMapping.find { it ->
				it.getKey() == key
			}?.getKey()
		}
		if(propKey) {
			writable = metadataToResourceMapping[propKey].writeable
		}
		[propKey, writable]
	}

	private parseQueryParams(location) {
		def sessionUri = new URL(location)
		def queryParams = sessionUri.query?.split('&') // safe operator for urls without query params
		def mapParams = queryParams.collectEntries { param ->
			param.split('=').collect {
				URLDecoder.decode(it)
			}
		}
		return mapParams
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
