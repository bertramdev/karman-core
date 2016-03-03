package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.network.NetworkProvider
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Commons
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
 * Network provider implementation for the Openstack Networking API
 * This is the starting point from which all calls to openstack networking originate.
 * <p>
 * Below is an example of how this might be initialized.
 * </p>
 * <pre>
 * {@code
 * import com.bertramlabs.plugins.karman.network.NetworkProvider
 * def provider = NetworkProvider.create(
 *  provider: 'openstack',
 *  username: 'myusername',
 *  apiKey: 'api-key-here',
 *  identityUrl:
 *  region: 'IAD'
 * )
 *
 * }
 * </pre>
 *
 * @author David Estes
 */
@Commons
public class OpenstackNetworkProvider extends NetworkProvider {
	static String providerName = "openstack"

	String username
	String apiKey = ''
	String region = 'IAD'
	String identityUrl = ''
	String password
	String tenantName
	String domainId = 'default'
	Map accessInfo
	String apiVersion='2.0'

	protected Boolean authenticate() {
		try {
			def authMap
			URIBuilder uriBuilder = new URIBuilder(identityUrl)
			def path = uriBuilder.getPath() ?: 'v2.0';
			HttpResponse response
			if (path.indexOf('v2.0') > 0) {
				apiVersion = '2.0'
				if(apiKey) {
					authMap = [
						auth: [
							"RAX-KSKEY:apiKeyCredentials": [
								username: this.username,
								apiKey: this.apiKey
							]
						]
					]
				} else if(password) {
					//v3 api
//					authMap = [
//						auth:[
//							identity: [
//								methods:['password'],
//								password:[
//									user:[
//										name:this.username,
//										password:this.password,
//										domain:[id:this.domainId ?: 'default']
//									]
//								]
//							]
//						]
//					]
					authMap = [auth:[passwordCredentials:[username:this.username, password:this.password]]]
					if(tenantName) {
						authMap.auth.tenantName = tenantName
					}
				}

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
					log.error("Authentication Request Failed ${response.getStatusLine().statusCode} when trying to connect to Openstack Cloud")
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
					log.error("Authentication Request Failed ${response.getStatusLine().statusCode} when trying to connect to Openstack Cloud")
					EntityUtils.consume(response.entity)
					return false
				}

				String responseText = responseEntity.content.text
				log.info("Auth response: ${responseText}")
				accessInfo = new JsonSlurper().parseText(responseText)
				accessInfo += [
					auth:[
						token:response.getHeaders('X-Storage-Token')[0].value,
						storageUrl:response.getHeaders('X-Storage-Url')[0].value
					]
				]
			}

			EntityUtils.consume(response.entity)
			return true
		} catch(ex) {
			log.error("Error occurred during the authentication phase - ${ex.message}",ex)
			return false
		}
	}



	public String getEndpointUrl() {
		if(!accessInfo) {
			if(!authenticate()) {
				return null
			}
		}

		if (accessInfo.networking) {
			return accessInfo.networking[accessInfo.networking.default]
		}
		else {
			def endpoints = accessInfo?.access?.serviceCatalog?.find{ it.type == 'network'}?.endpoints
			def endpointUrl = endpoints?.find{it.region == region}?.publicURL?.toString()?.trim()
			if(!endpointUrl) {
				endpointUrl = endpoints ? endpoints[0].publicURL?.toString()?.trim() : null
			}
			return endpointUrl
		}
	}

	public String getToken() {
		if(!accessInfo) {
			if(!authenticate()) {
				return null
			}
		}

		return accessInfo?.access?.token?.id?.toString() ?: accessInfo?.auth?.token
	}


	@Override
	public String getProviderName() {
		return null
	}

	@Override
	public Collection<SecurityGroupInterface> getSecurityGroups(Map options) {
		return null
	}

	@Override
	public Collection<SecurityGroupInterface> getSecurityGroups() {
		URIBuilder uriBuilder = new URIBuilder(endpointUrl)
		uriBuilder.setPath("/${apiVersion}/security-groups")
		HttpGet request = new HttpGet(uriBuilder.build())

		request.addHeader("Accept", "application/json")
		request.addHeader(new BasicHeader('X-Auth-Token', this.getToken()))

		HttpClient client = new DefaultHttpClient()
		HttpParams params = client.getParams()
		HttpConnectionParams.setConnectionTimeout(params, 30000)
		HttpConnectionParams.setSoTimeout(params, 20000)
		response = client.execute(request)
		HttpEntity responseEntity = response.getEntity();
		if(response.getStatusLine().statusCode != 200) {
			log.error("Security Group Request Failed ${response.getStatusLine().statusCode} when trying to connect to Openstack Cloud")
			EntityUtils.consume(response.entity)
			return null
		}

		String responseText = responseEntity.content.text

		def responseJson = new JsonSlurper().parseText(responseText)
		def provider = this
		return responseJson.security_groups?.collect { securityGroupMeta ->
			return new OpenstackSecurityGroup(provider, securityGroupMeta)
		}
	}

	@Override
	public SecurityGroupInterface getSecurityGroup(String uid) {
		return null
	}

	@Override
	public SecurityGroupInterface createSecurityGroup(String name) {
		return null
	}


}
