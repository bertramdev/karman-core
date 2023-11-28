package com.bertramlabs.plugins.karman.openstack

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Commons
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.NTCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.utils.URIBuilder
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.HttpConnectionFactory
import org.apache.http.conn.ManagedHttpClientConnection
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.X509HostnameVerifier
import org.apache.http.conn.ssl.SSLContextBuilder
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.client.config.RequestConfig
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.ProxyAuthenticationStrategy
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory
import org.apache.http.io.HttpMessageParserFactory
import org.apache.http.io.HttpMessageWriterFactory
import org.apache.http.message.BasicHeader
import org.apache.http.protocol.HttpContext
import org.apache.http.util.EntityUtils

import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import java.lang.reflect.InvocationTargetException
import java.security.cert.X509Certificate

import javax.net.ssl.SSLContext

@Commons
class OpenstackApiClient {

	String username
	String apiKey = ''
	String region
	String identityUrl = ''
	String password
	String tenantName
	String domainId = 'default'
	Map accessInfo
	String proxyHost
	Integer proxyPort
	String proxyUser
	String noProxy
	String proxyPassword
	String proxyWorkstation
	String proxyDomain
	String domainScopeType = 'id'
	String token

	Map getAccessInfo() {
		if(!accessInfo && !authenticate()) {
			return null
		}
		return accessInfo
	}

	String getToken() {
		if(!token) {
			token = getAccessInfo()?.authToken
		}
		return token
	}

	String getProjectId() {
		return getAccessInfo()?.projectId
	}

	String getApiEndpoint(String type) {
		return getAccessInfo()?.endpointInfo?.getAt("${type}Api")
	}

	String getApiEndpointVersion(String type) {
		return getAccessInfo()?.endpointInfo?.getAt("${type}Version")
	}

	callApi(url, path, opts = [:], method = 'GET') {
		def rtn = [success: false, headers: [:]]
		if(!url) {
			log.warn("callApi: blank url provided for path: ${path}")
			return rtn
		}
		try {
			String token = getAccessInfo().authToken

			URIBuilder uriBuilder = new URIBuilder(url)
			def existingPath = uriBuilder.getPath()
			def tmpPath = existingPath ? existingPath + path : path
			uriBuilder.setPath(tmpPath)
			if(opts.query) {
				opts.query.each { k,v -> uriBuilder.addParameter(k, v)}
			}
			HttpRequestBase request
			if(method == 'GET') {
				request = new HttpGet(uriBuilder.build())
			} else if(method == 'POST') {
				request = new HttpPost(uriBuilder.build())
				if(opts.body) {
					request.addHeader("Content-Type", "application/json");
					request.setEntity(new StringEntity(new JsonBuilder(opts.body).toString()))
				}
			} else if(method == 'PUT') {
				request = new HttpPut(uriBuilder.build())
				if(opts.body) {
					request.addHeader("Content-Type", "application/json");
					request.setEntity(new StringEntity(new JsonBuilder(opts.body).toString()))
				}
			} else if(method == 'DELETE') {
				request = new HttpDelete(uriBuilder.build())
			}
			request.addHeader("Accept", "application/json")
			request.addHeader(new BasicHeader('X-Auth-Token', token))
			if(opts.headers) {
				opts.headers.each { k, v -> request.addHeader(k, v) }
			}

			withHttpClient() { HttpClient client ->
				log.info "Calling: ${uriBuilder.build()} : ${method} with ${opts.body}"
				HttpResponse response = client.execute(request)
				HttpEntity responseEntity = response.getEntity();
				String responseText = responseEntity?.content?.text
				Integer responseStatusCode = response.getStatusLine().statusCode
				log.debug("Result: ${responseText}")

				rtn.statusCode = responseStatusCode
				if(responseText) {
					rtn.content = new JsonSlurper().parseText(responseText)
				}
				if(responseStatusCode >= 200 && response.getStatusLine().statusCode < 300) {
					rtn.success = true
				} else {
					log.error("Request Failed ${responseStatusCode} when trying to connect to Openstack Cloud: ${responseText}")
					EntityUtils.consume(responseEntity)
					rtn.success = false
					rtn.error = getApiError(rtn.content)
					rtn.statusReason = response.getStatusLine().reasonPhrase
				}
			}

		} catch(e) {
			log.error "Error in calling api: ${e}", e
			rtn.error = e.message
		}
		rtn
	}

	protected Boolean authenticate() {
		try {
			def authMap
			URIBuilder uriBuilder = new URIBuilder(identityUrl)
			def identityVersion = parseEndpointVersion(identityUrl) ? "/${parseEndpointVersion(identityUrl)}" : '/v3'
			HttpResponse response
			if(identityVersion == '/v3') {
				def basePath = uriBuilder.getPath()
				if(!basePath.contains(identityVersion)) {
					basePath += identityVersion
				}
				uriBuilder.setPath([basePath.endsWith("/") ? basePath.substring(0, basePath.size() - 1) : basePath, "auth", "tokens"].join("/"))
				log.debug("Auth url: ${uriBuilder.build()}")
				HttpPost authPost = new HttpPost(uriBuilder.build())
				authPost.addHeader("Content-Type", "application/json");

				authMap = [auth: [identity: [methods: ['password'], password: [user: [name: this.username, password: this.password, domain: [id: this.domainId ?: 'default']]]]]]
				if(this.tenantName) {
					authMap.auth.scope = [project: [name: this.tenantName, domain: [id:this.domainId ?: 'default']]]
					if(this.domainScopeType == 'name') {
						authMap.auth.identity.password.user.domain.remove('id')
						authMap.auth.identity.password.user.domain.name = this.domainId ?: 'default'
						authMap.auth.scope.project.domain.remove('id')
						authMap.auth.scope.project.domain.name = this.domainId ?: 'default'
					}
				}

				log.debug("Authentication w/ domain ID Map: ${authMap}")
				authPost.setEntity(new StringEntity(new JsonBuilder(authMap).toString()))

				withHttpClient() { HttpClient client ->
					response = client.execute(authPost)
					HttpEntity responseEntity = response.getEntity();

					if(response.getStatusLine().statusCode == 400 || response.getStatusLine().statusCode == 401) {
						log.debug("Failed to authenticate using domain ID, trying domain name")
						// Legacy migration path, attempt to auth using the domain ID input as the domain name instead of domain ID.
						authMap = [auth:[identity:[methods:['password'], password:[user:[name:this.username, password:this.password, domain:[name:this.domainId ?: 'default']]]]]]
						authMap.auth.scope = [project: [name: this.tenantName, domain: [name:this.domainId ?: 'default']]]
						authPost.setEntity(new StringEntity(new JsonBuilder(authMap).toString()))

						log.debug("Authentication w/ domain name Map: ${authMap}")
						response = client.execute(authPost)
						responseEntity = response.getEntity();
					}


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
				}


			} else {
				if(apiKey) {
					authMap = [
						auth: [
							"RAX-KSKEY:apiKeyCredentials": [
								username: this.username,
								apiKey  : this.apiKey
							]
						]
					]
				} else if(password) {
					authMap = [auth: [passwordCredentials: [username: this.username, password: this.password]]]
					if(tenantName) {
						authMap.auth.tenantName = tenantName
					}
				}

				uriBuilder.setPath([identityVersion.endsWith("/") ? identityVersion.substring(0, identityVersion.size() - 1) : identityVersion, "tokens"].join("/"))
				log.debug("Auth url: ${uriBuilder.build()}")
				HttpPost authPost = new HttpPost(uriBuilder.build())
				authPost.addHeader("Content-Type", "application/json");
				authPost.setEntity(new StringEntity(new JsonBuilder(authMap).toString()))

				withHttpClient() { HttpClient client ->
					response = client.execute(authPost)
					HttpEntity responseEntity = response.getEntity();
					if(response.getStatusLine().statusCode != 200) {
						log.error("Authentication Request Failed ${response.getStatusLine().statusCode} when trying to connect to Openstack Cloud")
						EntityUtils.consume(response.entity)
						return false
					}

					String responseText = responseEntity.content.text
					accessInfo = new JsonSlurper().parseText(responseText)
					accessInfo.identityApiVersion = '2.0'
					accessInfo.projectId = accessInfo?.access?.token.tenant.id
					accessInfo.authToken = accessInfo?.access?.token?.id?.toString()
				}


			}

			setEndpoints(accessInfo)
			EntityUtils.consume(response.entity)

			return true
		} catch(ex) {
			log.error("Error occurred during the authentication phase - ${ex.message}", ex)
			return false
		}
	}

	private setEndpoints(tokenResults) {
		def rtn = [success: false, errors: []]
		def identityUri = new java.net.URI(this.identityUrl)
		def osHost = identityUri.getHost()
		def serviceCatalog = accessInfo?.identityApiVersion == 'v3' ? tokenResults?.token?.catalog : tokenResults?.access?.serviceCatalog
		def endpointTypes = [
			[name:'compute', accessLabel:'compute', version:[noDot:false, defaultValue:null], required: true],
			[name:'image', accessLabel:'image', version:[noDot:true, defaultValue:'v2'], required: true],
			[name:'volume', accessLabel:'storage', version:[noDot:false, defaultValue:null], required: true],
			[name:'network', accessLabel:'network', version:[noDot:false, defaultValue:'v2.0'], required: true],
			[name:'load-balancer', accessLabel:'loadBalancer', version:[noDot:false, defaultValue:'v2.0'], required: false],
			[name:'elb', accessLabel:'loadBalancer', version:[noDot:false, defaultValue:'v1.0'], required: false],
			[name:'elbv1', accessLabel:'loadBalancerV1', version:[noDot:false, defaultValue:'v1.0'], required: false],
			[name:'object', accessLabel:'objectStorage', version:[noDot:true, defaultValue:'v1'], required: false],
			[name:'share', accessLabel:'sharedFileSystem', version:[noDot:true, defaultValue:'v1'], required: false]
		]
		if(!this.accessInfo) {
			this.accessInfo = [:]
		}
		this.accessInfo.endpointInfo = [:]
		endpointTypes.each { endpointType ->
			def endpointsForType = findEndpointsForType(serviceCatalog, endpointType.name)
			if(region) {
				endpointsForType = filterEndpointsForRegion(endpointsForType, region)
			}
			def apiResults = findLatestEndpointInSet(endpointsForType)
			log.debug("setEndpoints: found available endpoints: ${apiResults}")
			def match = findEndpoint(apiResults?.endpoints, osHost)
			if(!match && endpointType.required) {
				log.error("Openstack: Failed to set endpoint for ${endpointType.name} API")
				rtn.errors << [(endpointType.name): "Failed to find endpoint."]
			} else if(match) {
				this.accessInfo.endpointInfo["${endpointType.accessLabel}Api"] = match ? parseEndpoint(match) : null
				this.accessInfo.endpointInfo["${endpointType.accessLabel}Version"] = match ? parseEndpointVersion(match, endpointType.version.noDot, endpointType.version.defaultValue) : null
			}
		}

		if(rtn.errors.size() == 0) {
			rtn.success = true
		}

		return rtn
	}

	private findEndpointsForType(ArrayList serviceCatalog, String type) {
		return serviceCatalog?.findAll { it.type.replaceAll(/v\d+$/, '') == type || it.type == type }
	}

	private filterEndpointsForRegion(ArrayList endpointTypes, String tmpRegion) {
		endpointTypes.each { endpointType ->
			def regionEndpoints = endpointType.endpoints.findAll { endpoint -> endpoint.region == tmpRegion }
			endpointType.endpoints = regionEndpoints
		}

		return endpointTypes
	}

	private findLatestEndpointInSet(ArrayList endpoints) {
		return endpoints?.sort {a,b -> b.type <=> a.type }?.getAt(0)
	}

	private findEndpoint(endpoints, osHost) {
		def rtn
		def endpoint
		try {
			if(endpoints && endpoints.size() > 0) {
				// try to find public endpoints first
				def publicEndpoints = endpoints.findAll { it.interface == "public" }
				if(publicEndpoints.size() == 1) {
					endpoint = publicEndpoints.getAt(0)
					rtn = endpoint.url
				} else if(publicEndpoints.size() > 0) {
					// find best public??? (probably not a likely case)
					endpoint = publicEndpoints?.find { doesEndpointContainHost(it, osHost) }
					if(!endpoint) {
						endpoint = publicEndpoints.getAt(0)
					}
					rtn = endpoint?.url
				} else {
					// if no public endpoints found then do our best to find a usable endpoint (legacy endpoint detection method)
					endpoint = endpoints?.find { doesEndpointContainHost(it, osHost) }
					if(!endpoint) {
						osHost = osHost.substring(osHost.indexOf('.') + 1)
						endpoint = endpoints?.find { doesEndpointContainHost(it, osHost) }
					}
					endpoint = endpoint ?: endpoints?.first()
					if(endpoint) {
						log.debug("findEndpoint: using endpoint :${endpoint}")
						rtn = [endpoint.publicURL, endpoint.url, endpoint.adminURL].find { it && it.indexOf(osHost) > -1 }
					}

					if(!rtn) {
						/// Endpoint doesn't match osHost
						endpoint = endpoints.find { it.interface == "public" }
						rtn = endpoint?.url
					}
				}
			}
		} catch(e) {
			log.error("Openstack, Error parsing endpoint host: ${e}", e)
		}
		return rtn
	}

	private Boolean doesEndpointContainHost(endpoint, osHost) {
		return endpoint.publicURL?.indexOf(osHost) > -1 || endpoint.adminURL?.indexOf(osHost) > -1 || endpoint.internalURL?.indexOf(osHost) > -1 || endpoint.url?.indexOf(osHost) > -1
	}

	private parseEndpoint(osUrl) {
		def rtn = osUrl
		def hostStart = osUrl.toLowerCase().indexOf("://")
		def hostStartStr = osUrl.substring(0, hostStart + 3)
		def matchStartStr = osUrl.substring(hostStartStr.length() - 1)
		def pathArgs = matchStartStr.tokenize('/')
		def versionIdx = -1
		if(pathArgs.size() > 0) {
			versionIdx = pathArgs.findIndexOf { it =~ /^v\d(\.\d)*/ }
		}
		if(versionIdx >= 0) {
			rtn = hostStartStr + pathArgs[0..<versionIdx].join("/")
		}
		return rtn
	}

	private parseEndpointVersion(osUrl, noDot = false, defaultValue = '') {
		def rtn
		def url = new URL(osUrl)
		def pathArgs = url.path?.tokenize('/')?.findAll{it}
		if(pathArgs.size() > 0) {
			rtn = pathArgs.find { it =~ /^v\d(\.\d)*/ }
		}
		if(!rtn) {
			rtn = defaultValue
		}
		if(rtn?.length() > 0 && noDot == true) {
			def dotIndex = rtn.indexOf('.')
			if(dotIndex > -1)
				rtn = rtn.substring(0, dotIndex)
		}
		return rtn
	}

	private getApiError(Map responseContent) {
		def rtn
		try {
			if(responseContent) {
				if(responseContent['NeutronError']?.message) {
					rtn = responseContent['NeutronError'].message
				} else if(responseContent.badRequest?.message) {
					rtn = responseContent.badRequest.message
				} else if(responseContent.message) {
					try {
						def errorMsg = new JsonSlurper().parseText(responseContent.message)
						rtn = errorMsg['NeutronError']?.message
					} catch (e) {
						rtn = responseContent.message
					}
				} else if(responseContent.faultstring) {
					rtn = responseContent.faultstring
				} else if(responseContent.error) {
					if(responseContent.error instanceof Map && responseContent.error.message) {
						rtn = responseContent.error.message.toString()
					} else {
						rtn = responseContent.error.toString()
					}
				}
			}
			rtn = rtn ?: 'unknown error'
		} catch(Exception e) {
			log.error("getNeutronError error: {}", e, e)
		}
		return rtn
	}

	private HttpClient withHttpClient(Closure cl) {
		HttpClientBuilder clientBuilder = HttpClients.custom()

		clientBuilder.setHostnameVerifier(new X509HostnameVerifier() {
			public boolean verify(String host, SSLSession sess) {
				return true
			}

			public void verify(String host, SSLSocket ssl) {}

			public void verify(String host, String[] cns, String[] subjectAlts) {}

			public void verify(String host, X509Certificate cert) {}
		})

		//We want to ignore certificate errors for this
		SSLContext sslcontext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
			@Override
			public boolean isTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
				return true
			}
		}).build()

		SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(sslcontext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER) {
			@Override
			public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException, ConnectTimeoutException {
				if(socket instanceof SSLSocket) {
					try {
						socket.setEnabledProtocols(['SSLv3', 'TLSv1', 'TLSv1.1', 'TLSv1.2'] as String[])
						log.debug("hostname: ${host?.getHostName()}")
						PropertyUtils.setProperty(socket, "host", host.getHostName());
					} catch(NoSuchMethodException ex) {
					} catch(IllegalAccessException ex) {
					} catch(InvocationTargetException ex) {
					} catch(Exception ex) {
						log.error("We have an unhandled exception when attempting to connect to ${host} ignoring SSL errors", ex)
					}
				}
				return super.connectSocket(20 * 1000, socket, host, remoteAddress, localAddress, context)
			}
		}

		clientBuilder.setSSLSocketFactory(sslConnectionFactory)
		Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
			.register("https", sslConnectionFactory)
			.register("http", PlainConnectionSocketFactory.INSTANCE)
			.build();
		HttpMessageWriterFactory<HttpRequest> requestWriterFactory = new DefaultHttpRequestWriterFactory();
		HttpMessageParserFactory<HttpResponse> responseParserFactory = new DefaultHttpResponseParserFactory()
		HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory = new ManagedHttpClientConnectionFactory(
			requestWriterFactory, responseParserFactory);
		BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(registry, connFactory)
		clientBuilder.setConnectionManager(connectionManager)

		// Proxy Settings
		if(proxyHost) {
			clientBuilder.setProxy(new HttpHost(proxyHost, proxyPort))
			if(proxyUser) {
				CredentialsProvider credsProvider = new BasicCredentialsProvider();
				NTCredentials ntCreds = new NTCredentials(proxyUser, proxyPassword, proxyWorkstation, proxyDomain)
				credsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), ntCreds)

				clientBuilder.setDefaultCredentialsProvider(credsProvider)
				clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
			}
		}

		RequestConfig config = RequestConfig.custom()
			.setConnectTimeout(30000)
			.setSocketTimeout(20000).build()

		clientBuilder.setDefaultRequestConfig(config)
		def client = clientBuilder.build()
		try {
			cl.call(client)
		} finally {
			connectionManager.shutdown()
			return client
		}
	}
}
