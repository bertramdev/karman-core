package com.bertramlabs.plugins.karman.azure

import com.bertramlabs.plugins.karman.CloudFile
import groovy.util.logging.Commons
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPut
import org.apache.http.util.EntityUtils

@Commons
class AzurePrefix extends CloudFile {

	AzureDirectory parent
	String shareName

	protected String getType() {
		return 'directory'
	}

	@Override
	InputStream getInputStream() {
		return null
	}

	@Override
	void setInputStream(InputStream is) {

	}

	@Override
	OutputStream getOutputStream() {
		return null
	}

	@Override
	String getText(String encoding=null) {
		return null
	}


	@Override
	byte[] getBytes() {
		return new byte[0]
	}

	@Override
	void setText(String text) {

	}

	@Override
	void setBytes(Object bytes) {

	}

	@Override
	Long getContentLength() {
		return null
	}

	@Override
	String getContentType() {
		return null
	}

	@Override
	Date getDateModified() {
		return null
	}

	@Override
	void setContentType(String contentType) {

	}

	@Override
	Boolean exists() {
		AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

		def opts = [
			verb: 'GET',
			queryParams: [restype: type],
			path: getFullPath(),
			uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
		]

		def (HttpClient client, HttpGet request) = azureProvider.prepareRequest(opts)
		HttpResponse response = client.execute(request)
		HttpEntity responseEntity = response.getEntity()

		return (response.statusLine.statusCode == 200)
	}

	@Override
	Boolean isFile() {
		return false
	}

	@Override
	Boolean isDirectory() {
		return true
	}

	def save(acl) {
		if(!this.exists()) {
			AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

			def opts = [
				verb: 'PUT',
				queryParams: [restype: type],
				path: getFullPath(),
				uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
			]

			def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(opts)
			HttpResponse response = client.execute(request)
			HttpEntity responseEntity = response.getEntity()

			def saveSuccessful = (response.statusLine.statusCode == 201)
			if(saveSuccessful) {
				return true
			} else {
				def xmlDoc = new XmlSlurper().parse(responseEntity.content)
				EntityUtils.consume(response.entity)

				def errMessage = "Error saving ${getFullPath()}: ${xmlDoc.Message}"
				log.error errMessage
				throw new Exception(errMessage)
			}
		}
	}

	@Override
	/**
	 * Delete a directory
	 */
	def delete() {
		AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

		def opts = [
			verb: 'DELETE',
			queryParams: [restype: type],
			path: getFullPath(),
			uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
		]

		def (HttpClient client, HttpDelete request) = azureProvider.prepareRequest(opts)
		HttpResponse response = client.execute(request)
		HttpEntity responseEntity = response.getEntity()

		def deleteSuccessful = (response.statusLine.statusCode == 202)
		if(deleteSuccessful) {
			return true
		} else {
			def xmlDoc = new XmlSlurper().parse(responseEntity.content)
			EntityUtils.consume(response.entity)

			def errMessage = "Error deleting ${getFullPath()}: ${xmlDoc.Message}"
			log.error errMessage
			throw new Exception(errMessage)
		}
	}

	@Override
	void setMetaAttribute(Object key, Object value) {

	}

	@Override
	def getMetaAttribute(Object key) {
		return null
	}

	@Override
	def getMetaAttributes() {
		return null
	}

	@Override
	void removeMetaAttribute(Object key) {

	}

	/**
	 * List files and directories
	 * @param options prefix, delimiter
	 * @return List
	 */
	List listFiles(options = [:]) {
		log.info "listFiles from AzureDirectory path: ${getFullPath()}"
		AzureFileStorageProvider azureProvider = (AzureFileStorageProvider) provider

		// If 'prefix' is specified, then the request is relative to this directory
		// Construct the directory and return its listFiles
//		if(options.prefix) {
//			return processPrefixListFiles(options)
//		}

		def opts = [
			verb: 'GET',
			queryParams: [restype:'directory', comp: 'list'],
			path: getFullPath(),
			uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
		]
		if(options.prefix) {
			if(options.prefix.endsWith('/')) {
				opts.uri += "/" + options.prefix.substring(0,options.prefix.length()-1)
			} else {
				opts.uri += "/" + options.prefix
			}
		}

		def (HttpClient client, HttpGet request) = azureProvider.prepareRequest(opts)
		HttpResponse response = client.execute(request)
		HttpEntity responseEntity = response.getEntity()
		def xmlDoc = new XmlSlurper().parse(responseEntity.content)
		EntityUtils.consume(response.entity)

		def items = []
		if(response.statusLine.statusCode == 200) {
			xmlDoc.Entries?.File?.each { file ->
				items << new AzureFile(name: "${name}/${file.Name}", provider: provider, shareName: shareName)
			}
			xmlDoc.Entries?.Directory?.each { directory ->
				items << new AzurePrefix(name: "${name}/${directory.Name}", provider: provider, shareName: shareName)
			}
		} else {
			def errMessage = "Error getting items from ${getFullPath()}: ${xmlDoc.Message}"
			log.error errMessage
		}

		return items
	}


	CloudFile getFile(String fullFileName) {
		// With the syntax of ['somedirectory/another directory/file.txt'] the path
		// may be a subdirectory of the current directory so construct the fullPath
		// correctly
		def fileName = (getType() == 'share' ? fullFileName : "${name}/${fullFileName}")
		new AzureFile(
			provider: provider,
			name: fileName,
			shareName: shareName
		)
	}

	CloudFile getDirectory(String fullDirectoryName) {
		def directoryName = (getType() == 'share' ? fullDirectoryName : "${name}/${fullDirectoryName}")
		new AzurePrefix(
			provider: provider,
			name: directoryName,
			shareName: shareName
		)
	}

	protected String getFullPath(encodedName = true) {
		if(encodedName) {
			def encodedFileName
			def path = ''
			encodedFileName = java.net.URLEncoder.encode(name, "UTF-8").replaceAll('\\+', '%20').replaceAll('%2F','/')
			return "${shareName}/${path}${encodedFileName}"
		} else {
			return "${shareName}/${name}"
		}
	}
}
