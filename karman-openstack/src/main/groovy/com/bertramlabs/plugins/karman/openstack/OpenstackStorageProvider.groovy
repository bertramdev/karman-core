package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils
import org.apache.http.client.utils.URIBuilder

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
@Slf4j
public class OpenstackStorageProvider extends StorageProvider<OpenstackDirectory> {
	static String providerName = "openstack"

	String username
	String apiKey = ''
	String region = 'IAD'
	String tempUrlKey = '68tT3un009'
	String identityUrl = ''
	Long chunkSize = 0l
	Map accessInfo
	String serviceApiEndpoint

	protected Boolean authenticate() {
		try {
			def authMap
			URIBuilder uriBuilder = new URIBuilder(identityUrl)
			def path = uriBuilder.getPath() ?: 'v2.0';
			HttpResponse response
			if (path.indexOf('v2.0') > 0 ) {
				authMap = [
					auth: [
						"RAX-KSKEY:apiKeyCredentials": [
							username: this.username,
							apiKey: this.apiKey
						]
					]
				]
				uriBuilder.setPath([path.endsWith("/") ? path.substring(0,path.size() - 1) : path,"tokens"].join("/"))
				log.info("Auth url: ${uriBuilder.build()}")
				HttpPost authPost = new HttpPost(uriBuilder.build())
				authPost.addHeader("Content-Type","application/json");
				authPost.setEntity(new StringEntity(new JsonBuilder(authMap).toString()))
				HttpClient client = new DefaultHttpClient()
				HttpParams params = client.getParams()
				HttpConnectionParams.setConnectionTimeout(params, 30000)
				HttpConnectionParams.setSoTimeout(params, 20000)
				response = client.execute(authPost)
				HttpEntity responseEntity = response.getEntity();
				if(response.getStatusLine().statusCode != 200) {
					log.error("Authentication Request Failed ${response.getStatusLine().statusCode} when trying to connect to Openstack Swift Cloud")
					EntityUtils.consume(response.entity)
					return false
				}

				String responseText = responseEntity.content.text
				accessInfo = new JsonSlurper().parseText(responseText)
			}
			else {
				log.info("Auth url: ${uriBuilder.build()}")
				HttpGet authGet = new HttpGet(uriBuilder.build())
				authGet.addHeader("Content-Type","application/json");
				authGet.addHeader("X-Auth-User",this.username)
				authGet.addHeader("X-Auth-Key",this.apiKey)
				HttpClient client = new DefaultHttpClient()
				HttpParams params = client.getParams()
				HttpConnectionParams.setConnectionTimeout(params, 30000)
				HttpConnectionParams.setSoTimeout(params, 20000)
				response = client.execute(authGet)
				HttpEntity responseEntity = response.getEntity();
				if(response.getStatusLine().statusCode != 200) {
					log.error("Authentication Request Failed ${response.getStatusLine().statusCode} when trying to connect to Openstack Swift Cloud")
					EntityUtils.consume(response.entity)
					return false
				}

				String responseText = responseEntity.content.text
				log.info("Auth response: ${responseText}")
				if(responseText) {
					try {
						accessInfo = new JsonSlurper().parseText(responseText) ?: [:]	
					} catch(je) {
						//parse error, ignore for v1
						accessInfo = [:]
					}
					
				}
				accessInfo += [
				    auth:[
				        token:response.getHeaders('X-Storage-Token')[0].value,
					    storageUrl:response.getHeaders('X-Storage-Url')[0].value
				    ]
				]
			}

			if(tempUrlKey) {
				applyTempUrlKey()
			}
			EntityUtils.consume(response.entity)
			return true
		} catch(ex) {
			log.error("Error occurred during the authentication phase - ${ex.message}",ex)
			return false
		}
	}


	protected void applyTempUrlKey() {
		HttpPost request = new HttpPost(getEndpointUrl())

		request.addHeader("Accept", "application/json")
		request.addHeader(new BasicHeader('X-Auth-Token', getToken()))
		request.addHeader("X-Account-Meta-Temp-Url-Key", tempUrlKey)
		HttpClient client = new DefaultHttpClient()
		HttpParams params = client.getParams()
		HttpConnectionParams.setConnectionTimeout(params, 30000)
		HttpConnectionParams.setSoTimeout(params, 20000)
		HttpResponse response = client.execute(request)
		EntityUtils.consume(response.entity)
		if(response.statusLine.statusCode >= 300 || response.statusLine.statusCode < 200) {
			log.error("Error applying url key ${response.statusLine.statusCode}:${response.statusLine.reasonPhrase}")
			return
		}
	}

	public String getEndpointUrl() {
		if(!accessInfo) {
			if(!authenticate()) {
				return null
			}
		}

		if(this.serviceApiEndpoint) {
			return this.serviceApiEndpoint
		}

		if(accessInfo.auth.storageUrl) {
			return accessInfo.auth.storageUrl
		}

		if (accessInfo.storage) {
			return accessInfo.storage[accessInfo.storage.default]
		}
		else {
			def endpoints = accessInfo?.access?.serviceCatalog?.find{ it.type == 'object-store'}?.endpoints
			return endpoints?.find{it.region == region}?.publicURL?.toString().trim()
		}
	}

	public String getTenantId() {
		if(!accessInfo) {
			if(!authenticate()) {
				return null
			}
		}

		def endpoints = accessInfo?.access?.serviceCatalog?.find{ it.type == 'object-store'}?.endpoints
		return endpoints?.find{it.region == region}?.tenantId
	}


	public String getToken() {
		if(!accessInfo) {
			if(!authenticate()) {
				return null
			}
		}
		return accessInfo?.access?.token?.id?.toString() ?: accessInfo?.auth?.token
	}

	OpenstackDirectory getDirectory(String name) {
		new OpenstackDirectory(name: name, provider: this)
	}


	List<OpenstackDirectory> getDirectories() {
		if(!accessInfo) {
			authenticate()
		}

		HttpGet request = new HttpGet(getEndpointUrl())

		request.addHeader("Accept", "application/json")
		request.addHeader(new BasicHeader('X-Auth-Token', getToken()))
		HttpClient client = new DefaultHttpClient()
		HttpParams params = client.getParams()
		HttpConnectionParams.setConnectionTimeout(params, 30000)
		HttpConnectionParams.setSoTimeout(params, 20000)
		HttpResponse response = client.execute(request)

		if(response.statusLine.statusCode != 200) {
			log.error("Error fetching Directory List ${response.statusLine.statusCode}")
			EntityUtils.consume(response.entity)
			return null
		}
		HttpEntity responseEntity = response.getEntity()
		def jsonBody = new JsonSlurper().parse(new InputStreamReader(responseEntity.content))
		EntityUtils.consume(response.entity)
		def provider = this
		return jsonBody.collect { jsonObj ->
			new OpenstackDirectory(name: jsonObj.name, provider: provider)
		}
	}
}
