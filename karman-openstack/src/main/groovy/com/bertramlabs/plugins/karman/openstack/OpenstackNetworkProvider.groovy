package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.network.NetworkProvider
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Commons
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.*
import org.apache.http.entity.*
import org.apache.http.impl.client.*
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
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
 * @author Bob Whiton
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

	protected Boolean authenticate() {
		try {
			def authMap
			URIBuilder uriBuilder = new URIBuilder(identityUrl)
			def identityVersion = parseEndpointVersion(identityUrl) ? "/${parseEndpointVersion(identityUrl)}" : '/v3'
			HttpResponse response
			if (identityVersion == '/v3') {
				uriBuilder.setPath([identityVersion.endsWith("/") ? identityVersion.substring(0,identityVersion.size() - 1) : identityVersion, "auth", "tokens"].join("/"))
				log.info("Auth url: ${uriBuilder.build()}")
				HttpPost authPost = new HttpPost(uriBuilder.build())
				authPost.addHeader("Content-Type","application/json");
				authMap = [auth:[identity:[methods:['password'], password:[user:[name:this.username, password:this.password, domain:[id:this.domainId ?: 'default']]]]]]
				authPost.setEntity(new StringEntity(new JsonBuilder(authMap).toString()))
				HttpClient client = new DefaultHttpClient()
				HttpParams params = client.getParams()
				HttpConnectionParams.setConnectionTimeout(params, 30000)
				HttpConnectionParams.setSoTimeout(params, 20000)
				response = client.execute(authPost)
				HttpEntity responseEntity = response.getEntity();
				if(response.getStatusLine().statusCode != 201) {
					log.error("Authentication Request Failed ${response.getStatusLine().statusCode} when trying to connect to Openstack Cloud")
					EntityUtils.consume(response.entity)
					return false
				}

				String responseText = responseEntity.content.text
				accessInfo = new JsonSlurper().parseText(responseText)
				accessInfo.projectId = accessInfo.token.project.id
				accessInfo.identityApiVersion = 'v3'
				accessInfo.authToken = response.getHeaders('X-Subject-Token')[0].value
			} else {
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
					authMap = [auth:[passwordCredentials:[username:this.username, password:this.password]]]
					if(tenantName) {
						authMap.auth.tenantName = tenantName
					}
				}

				uriBuilder.setPath([identityVersion.endsWith("/") ? identityVersion.substring(0,identityVersion.size() - 1) : identityVersion, "tokens"].join("/"))
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
				println "responseText: ${responseText}"
				accessInfo = new JsonSlurper().parseText(responseText)
				accessInfo.identityApiVersion = '2.0'
				accessInfo.projectId = accessInfo?.access?.token.tenant.id
				accessInfo.authToken = accessInfo?.access?.token?.id?.toString()
			}

			setEndpoints(accessInfo)

			EntityUtils.consume(response.entity)
			return true
		} catch(ex) {
			log.error("Error occurred during the authentication phase - ${ex.message}",ex)
			return false
		}
	}

	public Map getAccessInfo() {
		if(!accessInfo) {
			if(!authenticate()) {
				return null
			}
		}

		accessInfo
	}

	@Override
	public String getProviderName() {
		return OpenstackNetworkProvider.providerName
	}

	@Override
	public Collection<SecurityGroupInterface> getSecurityGroups(Map options) {
		return this.getSecurityGroups()
	}

	@Override
	public Collection<SecurityGroupInterface> getSecurityGroups() {
		def accessInfo = getAccessInfo()
		def result = callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-groups")
		if(!result.success) {
			throw new RuntimeException("Error in obtaining security groups: ${result.error}")
		}
		def provider = this
		return result.content?.security_groups?.collect { securityGroupMeta ->
			return new OpenstackSecurityGroup(provider, securityGroupMeta)
		}
	}

	@Override
	public SecurityGroupInterface getSecurityGroup(String uid) {
		def accessInfo = getAccessInfo()
		def result = callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-groups/${uid}")
		if(!result.success) {
			throw new RuntimeException("Error in obtaining security group: ${result.error}")
		}
		return new OpenstackSecurityGroup(this, result.content?.security_group)
	}

	@Override
	public SecurityGroupInterface createSecurityGroup(String name) {
		return new OpenstackSecurityGroup(this, [name: name])
	}

	public callApi(url, path, opts = [:], method = 'GET') {
		def rtn = [success: false, headers: [:]]
		try {
			def token = getAccessInfo().authToken

			URIBuilder uriBuilder = new URIBuilder(url)
			uriBuilder.setPath(path)
			HttpRequestBase request
			if(method == 'GET') {
				request = new HttpGet(uriBuilder.build())	
			} else if (method == 'POST') {
				request = new HttpPost(uriBuilder.build())	
				if(opts.body) {
					request.addHeader("Content-Type","application/json");
					request.setEntity(new StringEntity(new JsonBuilder(opts.body).toString()))
				}
			} else if (method == 'PUT') {
				request = new HttpPut(uriBuilder.build())	
				if(opts.body) {
					request.addHeader("Content-Type","application/json");
					request.setEntity(new StringEntity(new JsonBuilder(opts.body).toString()))
				}
			} else if (method == 'DELETE') {
				request = new HttpDelete(uriBuilder.build())
			}
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', token))

			HttpClient client = new DefaultHttpClient()
			HttpParams params = client.getParams()
			HttpConnectionParams.setConnectionTimeout(params, 30000)
			HttpConnectionParams.setSoTimeout(params, 20000)

			log.info "Calling: ${uriBuilder.build()} : ${method} with ${opts.body}"

			HttpResponse response = client.execute(request)
			HttpEntity responseEntity = response.getEntity();
			String responseText = responseEntity.content.text
			log.info "  Result: ${responseText}"

			if(responseText) {
				rtn.content = new JsonSlurper().parseText(responseText)
			}
			if(response.getStatusLine().statusCode >= 200 && response.getStatusLine().statusCode < 300) {
				rtn.success = true
			} else {
				log.error("Request Failed ${response.getStatusLine().statusCode} when trying to connect to Openstack Cloud: ${responseEntity.content.text}")
				EntityUtils.consume(response.entity)
				rtn.success = false
			}
		} catch(e) {
			log.error "Error in calling api: ${e}", e
			rtn.error = e.message
		}
		rtn
	}

	private setEndpoints(tokenResults) {
		def identityUri = new java.net.URI(this.identityUrl)
		def osHost = identityUri.getHost()
		accessInfo.endpointInfo = [:]

		if(accessInfo.identityApiVersion == 'v3') {
			def computeApiResults = tokenResults.token.catalog.find{it.type == 'compute'}
			def match = findEndpointHost(computeApiResults?.endpoints, osHost)
			accessInfo.endpointInfo.computeApi = match ? parseEndpoint(match) : null
			accessInfo.endpointInfo.computeVersion = match ? parseEndpointVersion(match) : null
			def imageApiResults = tokenResults.token.catalog.find{it.type == 'image'}
			match = findEndpointHost(imageApiResults?.endpoints, osHost)
			accessInfo.endpointInfo.imageApi = match ? parseEndpoint(match) : null
			accessInfo.endpointInfo.imageVersion = match ? parseEndpointVersion(match, true, 'v2') : null
			def storageApiResults = tokenResults.token.catalog.find{it.type == 'volume'}
			match = findEndpointHost(storageApiResults?.endpoints, osHost)
			accessInfo.endpointInfo.storageApi = match ? parseEndpoint(match) : null
			accessInfo.endpointInfo.storageVersion = match ? parseEndpointVersion(match) : null
			def networkApiResults = tokenResults.token.catalog.find{it.type == 'network'}
			match = findEndpointHost(networkApiResults?.endpoints, osHost)
			accessInfo.endpointInfo.networkApi = match ? parseEndpoint(match) : null
			accessInfo.endpointInfo.networkVersion = match ? parseEndpointVersion(match) : null
		} else {
			def computeApiResults = tokenResults.access.serviceCatalog.find{it.type == 'compute'}
			def match = findEndpointHost(computeApiResults?.endpoints, osHost)
			accessInfo.endpointInfo.computeApi = match ? parseEndpoint(match) : null
			accessInfo.endpointInfo.computeVersion = match ? parseEndpointVersion(match) : null
			def imageApiResults = tokenResults.access.serviceCatalog.find{it.type == 'image'}
			match = findEndpointHost(imageApiResults?.endpoints, osHost)
			accessInfo.endpointInfo.imageApi = match ? parseEndpoint(match) : null
			accessInfo.endpointInfo.imageVersion = match ? parseEndpointVersion(match, true, 'v2') : null
			def storageApiResults = tokenResults.access.serviceCatalog.find{it.type == 'volume'}
			match = findEndpointHost(storageApiResults?.endpoints, osHost)
			accessInfo.endpointInfo.storageApi = match ? parseEndpoint(match) : null
			accessInfo.endpointInfo.storageVersion = match ? parseEndpointVersion(match) : null
			def networkApiResults = tokenResults.access.serviceCatalog.find{it.type == 'network'}
			match = findEndpointHost(networkApiResults?.endpoints, osHost)
			accessInfo.endpointInfo.networkApi = match ? parseEndpoint(match) : null
			accessInfo.endpointInfo.networkVersion = match ? parseEndpointVersion(match) : null
		}
	}

	private findEndpointHost(endpoints, osHost) {
		def rtn
		def endpoint = endpoints?.find{it.publicURL?.indexOf(osHost) > -1  || it.adminURL?.indexOf(osHost) > -1 || it.internalURL?.indexOf(osHost) > -1 || it.url?.indexOf(osHost) > -1}
		if(!endpoint) {
			osHost = osHost.substring(osHost.indexOf('.') + 1)
			endpoint = endpoints?.find{it.publicURL?.indexOf(osHost) > -1 || it.adminURL?.indexOf(osHost) > -1 || it.internalURL?.indexOf(osHost) > -1 || it.url?.indexOf(osHost) > -1}
		}
		if(!endpoint)
			endpoint = endpoints.first()
		if(endpoint) {
			rtn = (endpoint.publicURL && endpoint.publicURL.indexOf(osHost) > -1) ? endpoint.publicURL : null
			if(!rtn)
				rtn = (endpoint.url && endpoint.url.indexOf(osHost) > -1) ? endpoint.url : null
			if(!rtn)
				rtn = (endpoint.adminURL && endpoint.adminURL.indexOf(osHost) > -1) ? endpoint.adminURL : null
		}
		return rtn
	}

	private parseEndpoint(osUrl) {
		def rtn = osUrl
		def hostStart = osUrl.toLowerCase().indexOf("://")
		def firstSlash = osUrl.indexOf("/", hostStart + 3)
		if(firstSlash > -1)
			rtn = rtn.substring(0, firstSlash)
		return rtn
	}

	private parseEndpointVersion(osUrl, noDot = false, defaultValue = '') {
		def rtn
		def hostStart = osUrl.indexOf("://")
		def firstSlash = osUrl.indexOf("/", hostStart + 3)
		def secondSlash = firstSlash > -1 ? osUrl.indexOf("/", firstSlash + 1) : -1
		log.debug("parse: ${osUrl} - ${hostStart} - ${firstSlash} - ${secondSlash}")
		if(secondSlash > -1)
			rtn = osUrl.substring(firstSlash + 1, secondSlash)
		else if(firstSlash > -1)
			rtn = osUrl.substring(firstSlash + 1)
		else
			rtn = defaultValue
		if(rtn?.length() > 0 && noDot == true) {
			def dotIndex = rtn.indexOf('.')
			if(dotIndex > -1)
				rtn = rtn.substring(0, dotIndex)
		}
		return rtn
	}


}
