package com.bertramlabs.plugins.karman.openstack

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
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils

/**
 * Created by davidestes on 10/12/15.
 */
@Commons
class OpenstackDirectory extends Directory {

	/**
	 * Get the api client from the provider
	 */
	OpenstackApiClient getApiClient() {
		OpenstackStorageProvider storageProvider = (OpenstackStorageProvider) provider
		(OpenstackApiClient) storageProvider.getApiClient()
	}

	/**
	 * Check if bucket exists
	 * @return Boolean
	 */
	Boolean exists() {
		String endpointUrl = getApiClient().getApiEndpoint('objectStorage')
		String path = "/${name}"
		def results = getApiClient().callApi(endpointUrl, path, [:], 'HEAD')
		return results.success
	}

	/**
	 * List bucket files
	 * @param options (prefix, marker, delimiter and maxKeys)
	 * @return List
	 */
	List listFiles(options = [:]) {
		List rtn = []
		String endpointUrl = getApiClient().getApiEndpoint('objectStorage')
		String path = "/${name}"
		def results = getApiClient().callApi(endpointUrl, path, [query:options], 'GET')
		rtn = results.content?.collect { meta ->
			cloudFileFromOpenstackMeta(meta)
		}

		return rtn
	}

	/**
	 * Create bucket for a given region (default to region in config if not defined)
	 * @return Bucket
	 */
	def save() {
		log.debug("saveing directory ${name}")
		String endpointUrl = getApiClient().getApiEndpoint('objectStorage')
		String path = "/${name}"
		def results = getApiClient().callApi(endpointUrl, path, [:], 'PUT')

		return results.success
	}

	/**
	 * Delete bucket
	 * @return Boolean
	 */
	def delete() {
		log.debug("deleting directory ${name}")
		log.debug("saveing directory ${name}")
		String endpointUrl = getApiClient().getApiEndpoint('objectStorage')
		String path = "/${name}"
		def results = getApiClient().callApi(endpointUrl, path, [:], 'DELETE')

		return results.success
	}

	/**
	 * Get a file from the bucket
	 * @param name
	 * @return File
	 */
	CloudFile getFile(String name) {
		new OpenstackCloudFile(
			provider: provider,
			parent: this,
			name: name
		)
	}

	private CloudFile cloudFileFromOpenstackMeta(Map meta) {
		if(meta.subdir) {
			return new OpenstackSubDir(
				provider: provider,
				parent: this,
				name: meta.subdir[0..-2]
			)
		} else {
			return new OpenstackCloudFile(
				provider: provider,
				parent: this,
				name: meta.name
			)
		}

	}
}
