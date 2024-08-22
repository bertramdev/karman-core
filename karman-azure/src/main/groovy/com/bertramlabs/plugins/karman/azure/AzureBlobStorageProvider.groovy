package com.bertramlabs.plugins.karman.azure

import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider

import groovy.util.logging.Slf4j

import org.apache.http.HttpEntity
import org.apache.http.client.HttpClient
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils

import java.text.*;

/**
 * Storage provider implementation for the Azure Storage PageBlob and Container API
 * This is the starting point from which all calls to Azure originate for storing blobs within the Cloud File Containers
 * <p>
 * Below is an example of how this might be initialized.
 * </p>
 * <pre>
 * {@code
 * import com.bertramlabs.plugins.karman.StorageProvider
 * def provider = StorageProvider(
 *  provider: 'azure-pageblob',
 *  storageAccount: 'storage account name',
 *  storageKey: 'storage key'
 * )
 *
 * def blob = provider['container']['example.txt'] 
 * blob.setBytes(byteArray)
 * blob.save()
 * }
 * </pre>
 *
 * @author Bob Whiton
 */
@Slf4j
public class AzureBlobStorageProvider extends AzureStorageProvider<AzureContainer> {
	static String providerName = "azure-pageblob"

	public String getProviderName() {
		return providerName
	}

	@Override
	public String getEndpointUrl() {
		def base = baseEndpointDomain ?: 'core.windows.net'
		return "${protocol}://${storageAccount}.blob.${base}"
	}

	AzureContainer getDirectory(String name) {
		new AzureContainer(name: name, provider: this)
	}

	List<AzureContainer> getDirectories() {
		def opts = [
			verb: 'GET',
			queryParams: [comp: 'list'], 
			path: '',
			uri: "${getEndpointUrl()}".toString()
		]

		def (HttpClient client, HttpGet request) = prepareRequest(opts) 
		HttpResponse response = client.execute(request)
		if(response.statusLine.statusCode != 200) {
			HttpEntity responseEntity = response.getEntity()
			log.error("Error fetching Directory List ${response.statusLine.statusCode}, content: ${responseEntity.content}")
			EntityUtils.consume(response.entity)
			return null
		}

		HttpEntity responseEntity = response.getEntity()
		def xmlDoc = new XmlSlurper().parse(responseEntity.content)
		EntityUtils.consume(response.entity)
		
		def provider = this
		def directories = []
		xmlDoc.Containers?.Container?.each { container ->
			directories << new AzureContainer(name: container.Name, provider: provider)
		}
		return directories
	}
}
