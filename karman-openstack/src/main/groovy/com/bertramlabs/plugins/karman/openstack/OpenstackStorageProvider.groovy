package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import groovy.json.JsonSlurper
import groovy.util.logging.Commons
import org.apache.http.client.methods.*
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils

/**
 * Storage provider implementation for the Openstack Cloud Files API
 * This is the starting point from which all calls to openstack swift originate for storing files within the Cloud File Containers
 * <p>
 * Below is an example of how this might be initialized.
 * </p>
 * <pre>
 * {@code
 * import com.bertramlabs.plugins.karman.StorageProvider
 * def provider = StorageProvider(
 *  provider: 'openstack',
 *  username: 'myusername',
 *  apiKey: 'api-key-here',
 *  identityUrl:
 *  region: 'IAD'
 * )
 *
 * //Shorthand
 * provider['container']['example.txt'] = "This is a string I am storing."
 * //or
 * provider.'container'.'example.txt' = "This is a string I am storing."
 * }
 * </pre>
 *
 * @author David Estes
 */
@Commons
class OpenstackStorageProvider extends StorageProvider {

	static String providerName = "openstack"

	OpenstackApiClient apiClient
	Map configOptions

	String tempUrlKey = '68tT3un009'
	Long chunkSize = 0l

	/**
	 * Constructor for the OpenstackStorageProvider
	 * @param config Map of configuration options for the provider
	 */
	def OpenstackStorageProvider(Map config) {
		super(config)
		this.configOptions = config
		this.apiClient = new OpenstackApiClient(config)
	}

	@Override
	String getProviderName() {
		return OpenstackNetworkProvider.providerName
	}

	OpenstackApiClient getApiClient() {
		if(!apiClient) {
			apiClient = new OpenstackApiClient(configOptions)
		}
		return apiClient
	}

	String getEndpointUrl() {
		return apiClient.getApiEndpoint('objectStorage')?.toString()?.trim()
	}

	protected void applyTempUrlKey() {
		def headers = ["X-Account-Meta-Temp-Url-Key":tempUrlKey]
		def results = apiClient.callApi(getEndpointUrl(), '', [headers: headers], 'POST')
		if(results.statusCode >= 300 || results.statusCode < 200) {
			log.error("Error applying url key ${results.statusCode}:${results.statusReason}" + "${results.error ?: null}")
		}
	}

	Directory getDirectory(String name) {
		new OpenstackDirectory(name: name, provider: this)
	}

	List<Directory> getDirectories() {
		List<Directory> rtn = []
		def tmpEndpointUrl = getEndpointUrl()
		if(tmpEndpointUrl) {
			def results = apiClient.callApi(tmpEndpointUrl, '', [:], 'GET')
			if(results.success == true) {
				rtn = results.content.collect { dir ->
					new OpenstackDirectory(name: dir.name, provider: this)
				}
			} else {
				log.error("Error fetching Directory List ${response.statusLine.statusCode}")
				EntityUtils.consume(response.entity)
				return null
			}
		}

		return rtn
	}
}
