package com.bertramlabs.plugins.karman.azure

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
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
 * Created by bwhiton on 11/22/2016.
 */
@Slf4j
class AzureContainer extends Directory<AzurePageBlobFile> {
	/**
	 * Check if container exists
	 * @return Boolean
	 */
	Boolean exists() {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		def opts = [
			verb: 'GET',
			queryParams: [restype:'container'],
			path: name,
			uri: "${azureProvider.getEndpointUrl()}/${name}".toString()
		]
		
		def (HttpClient client, HttpGet request) = azureProvider.prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		HttpEntity responseEntity = response.getEntity()

		return (response.statusLine.statusCode == 200)
	}

	/**
	 * List container files
	 * @param options (prefix, marker, delimiter and maxKeys)
	 * @return List
	 */
	List<AzurePageBlobFile> listFiles(Map<String,Object> options = [:]) {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		def opts = [
			verb: 'GET',
			queryParams: [restype:'container', comp: 'list'],
			path: name,
			uri: "${azureProvider.getEndpointUrl()}/${name}".toString()
		]

		def (HttpClient client, HttpGet request) = azureProvider.prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		HttpEntity responseEntity = response.getEntity()
		def xmlDoc = new XmlSlurper().parse(responseEntity.content)
		EntityUtils.consume(response.entity)	

		def blobs = []
		if(response.statusLine.statusCode == 200) {
			xmlDoc.Blobs?.Blob?.each { blob ->
				blobs << new AzurePageBlobFile(parent: this, name: blob.Name, provider: provider)
			}
		} else {
			def errMessage = "Error getting blobs from directory with name ${opts.path}: ${xmlDoc.Message}"
			log.error errMessage
			throw new Exception(errMessage)
		}

		return blobs
	}

	/**
	 * Create container 
	 * @return Container
	 */
	void save() {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		def opts = [
			verb: 'PUT',
			queryParams: [restype:'container'],
			path: name,
			uri: "${azureProvider.getEndpointUrl()}/${name}".toString()
		]

		def (HttpClient client, HttpPut request) = azureProvider.prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		HttpEntity responseEntity = response.getEntity()
		
		def saveSuccessful = (response.statusLine.statusCode == 201)	
		if(saveSuccessful) {

		} else {
			def xmlDoc = new XmlSlurper().parse(responseEntity.content)
			EntityUtils.consume(response.entity)

			def errMessage = "Error saving directory with name ${opts.path}: ${xmlDoc.Message}"
			log.error errMessage
			throw new Exception(errMessage)
		}
	}

	/**
	 * Delete a container 
	 * @return Container
	 */
	void delete() {
		AzureBlobStorageProvider azureProvider = (AzureBlobStorageProvider) provider

		def opts = [
			verb: 'DELETE',
			queryParams: [restype:'container'],
			path: name,
			uri: "${azureProvider.getEndpointUrl()}/${name}".toString()
		]

		def (HttpClient client, HttpDelete request) = azureProvider.prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		HttpEntity responseEntity = response.getEntity()

		def deleteSuccessful = (response.statusLine.statusCode == 202)
		if(deleteSuccessful) {

		} else {
			def xmlDoc = new XmlSlurper().parse(responseEntity.content)
			EntityUtils.consume(response.entity)

			def errMessage = "Error deleting directory with name ${opts.path}: ${xmlDoc.Message}"
			log.error errMessage
			throw new Exception(errMessage)
		}
	}

	AzurePageBlobFile getFile(String name) {
		new AzurePageBlobFile(
			provider: provider,
			parent: this,
			name: name
		)
	}
}
