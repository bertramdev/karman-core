package com.bertramlabs.plugins.karman.azure

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import groovy.json.JsonSlurper
import groovy.util.logging.Commons
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils

/**
 * Created by bwhiton on 12/02/2016.
 */
@Commons
class AzureDirectory extends Directory {
	
	protected String shareName

	protected String getType() {
		return 'directory'
	}

	/**
	 * Check if the Directory exists
	 * @return Boolean
	 */
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
		if(options.prefix) {
			return processPrefixListFiles(options)
		} 

		def opts = [
			verb: 'GET',
			queryParams: [restype:'directory', comp: 'list'],
			path: getFullPath(),
			uri: "${azureProvider.getEndpointUrl()}/${getFullPath()}".toString()
		]

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
				items << new AzureDirectory(name: "${name}/${directory.Name}", provider: provider, shareName: shareName)
			}
		} else {
			def errMessage = "Error getting items from ${getFullPath()}: ${xmlDoc.Message}"
			log.error errMessage
		}

		return items
	}

	/**
	 * Create directory
	 */
	def save() {
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

	Directory getDirectory(String fullDirectoryName) {
		def directoryName = (getType() == 'share' ? fullDirectoryName : "${name}/${fullDirectoryName}") 
		new AzureDirectory(
			provider: provider,
			name: directoryName,
			shareName: shareName
		)
	}

	protected List processPrefixListFiles(options = [:]) {
		def subPath = options.prefix
		if(options.delimiter) {
			subPath = subPath.replace(options.delimiter, '/')
		}

		def currentDirectory = this
		subPath.tokenize('/')?.each{ part ->
			if(part) {
				currentDirectory = currentDirectory.getDirectory(part.toString())
			}
		}	

		return currentDirectory.listFiles()
	}


	protected String getFullPath(encodedName = true) {
		if(encodedName) {
			def lastSlash = name.lastIndexOf('/')
			def encodedFileName
			def path = ''
			encodedFileName = java.net.URLEncoder.encode(name, "UTF-8").replaceAll('\\+', '%20').replaceAll('%2F','/')
			return "${shareName}/${path}${encodedFileName}"
		} else {
			return "${shareName}/${name}"
		}
	}
}
