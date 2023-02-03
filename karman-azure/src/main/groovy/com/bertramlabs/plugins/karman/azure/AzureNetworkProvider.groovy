package com.bertramlabs.plugins.karman.azure

import com.bertramlabs.plugins.karman.network.NetworkProvider
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import groovy.util.logging.Commons
import groovy.json.JsonOutput
import org.apache.http.ssl.SSLContexts
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.ParseException
import org.apache.http.auth.AuthScope
import org.apache.http.auth.NTCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.commons.beanutils.PropertyUtils
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPatch
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.utils.URIBuilder
import org.apache.http.config.MessageConstraints
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.HttpConnectionFactory
import org.apache.http.conn.ManagedHttpClientConnection
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.SSLContextBuilder
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.conn.ssl.X509HostnameVerifier
import org.apache.http.entity.StringEntity
import org.apache.http.impl.DefaultHttpResponseFactory
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.ProxyAuthenticationStrategy
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.impl.conn.DefaultHttpResponseParser
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory
import org.apache.http.io.HttpMessageParser
import org.apache.http.io.HttpMessageParserFactory
import org.apache.http.io.HttpMessageWriterFactory
import org.apache.http.io.SessionInputBuffer
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicLineParser
import org.apache.http.message.BasicNameValuePair
import org.apache.http.message.LineParser
import org.apache.http.protocol.HttpContext
import org.apache.http.util.CharArrayBuffer
import org.apache.http.util.EntityUtils

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import java.lang.reflect.InvocationTargetException
import java.security.cert.X509Certificate

/**
 * Created by bwhiton on 05/13/2019.
 */
@Commons
class AzureNetworkProvider extends NetworkProvider {
	static String providerName = "azure"

	static Integer WEB_CONNECTION_TIMEOUT = 60 * 1000

	String subscriptionId
	String resourceGroup
	String identityUrl = 'https://login.microsoftonline.com'
	String identityPath
	String identityResourceUrl = 'https://management.core.windows.net'
	String managementUrl = 'https://management.azure.com'
	String location
	Boolean ignoreSSL = false
	String tenantId
	String clientId
	String clientKey

	String proxyHost
	Integer proxyPort
	String proxyUser
	String proxyPassword
	String proxyWorkstation
	String proxyDomain
	String protocol = 'https'

	String getProviderName() {
		return this.providerName
	}

	public AzureNetworkProvider(Map options) {
		subscriptionId = options.subscriptionId ?: subscriptionId
		resourceGroup = options.resourceGroup ?: resourceGroup
		identityUrl = options.identityUrl ?: identityUrl
		identityPath = options.identityPath ?: identityPath
		identityResourceUrl = options.identityResourceUrl ?: identityResourceUrl
		managementUrl = options.managementUrl ?: managementUrl
		ignoreSSL = options.ignoreSSL != null ? options.ignoreSSL : ignoreSSL
		tenantId = options.tenantId ?: tenantId
		clientId = options.clientId ?: clientId
		clientKey = options.clientKey ?: clientKey
		location = options.location ?: location

		protocol = options.protocol ?: protocol
		proxyHost = options.proxyHost ?: proxyHost
		proxyPort = options.proxyPort ?: proxyPort
		proxyUser = options.proxyUser ?: proxyUser
		proxyPassword = options.proxyPassword ?: proxyPassword
		proxyDomain = options.proxyDomain ?: proxyDomain
		proxyWorkstation = options.proxyWorkstation ?: proxyWorkstation
	}

	@Override
	Collection<SecurityGroupInterface> getSecurityGroups(Map options = [:]) {
		def securityGroups = []
		def apiPath
		if(resourceGroup) {
			apiPath = "/subscriptions/${subscriptionId}/resourceGroups/${resourceGroup}/providers/Microsoft.Network/networkSecurityGroups"
		} else {
			apiPath = "/subscriptions/${subscriptionId}/providers/Microsoft.Network/networkSecurityGroups"
		}

		def nextLink = apiPath
		while(nextLink) {
			def results = callApi(apiPath, [query: ['api-version': '2018-11-01'], token: options.token], 'GET')
			if (!results.success) {
				throw new Exception('Error in calling Azure api')
			}
			def parsedResults = new groovy.json.JsonSlurper().parseText(results.content)
			if (parsedResults?.error) {
				throw new RuntimeException(parsedResults?.error?.message ?: 'Error in getting Security Groups from Azure')
			}
			parsedResults?.value?.each {
				securityGroups << new AzureSecurityGroup(this, [id: it.id, name: it.name, resourceGroup: parseResourceGroupName(it.id), location: it.location, tags: it.tags, etag: it.etag, properties: it.properties])
			}
			nextLink = parsedResults.results?.nextLink ?: parsedResults.nextLink
			if(nextLink?.startsWith('http')) {
				nextLink = nextLink.toString().substring(nextLink.toString().indexOf('/subscriptions'))
			}
		}

		return securityGroups
	}

	@Override
	SecurityGroupInterface getSecurityGroup(String name) {
		def apiPath = "/subscriptions/${subscriptionId}/resourceGroups/${resourceGroup}/providers/Microsoft.Network/networkSecurityGroups/${name}"
		def results = callApi(apiPath, [query: ['api-version': '2018-11-01']], 'GET')
		def parsedResult = new groovy.json.JsonSlurper().parseText(results.content)
		if(parsedResult?.error) {
			throw new RuntimeException(parsedResult?.error?.message ?: 'Error in getting Security Group from Azure')
		}
		def securityGroup = new AzureSecurityGroup(this, [id:parsedResult.id, name:parsedResult.name, resourceGroup: resourceGroup, location:parsedResult.location, tags:parsedResult.tags, etag:parsedResult.etag, properties:parsedResult.properties])

		return securityGroup
	}

	SecurityGroupInterface createSecurityGroup(String name) {
		return new AzureSecurityGroup(this, [name: name, resourceGroup: resourceGroup, location: location])
	}

	public String getResourceGroup(){
		this.resourceGroup
	}

	public setResourceGroup(String resourceGroup) {
		this.resourceGroup = resourceGroup
	}

	private getToken() {
		def azureUrl = identityUrl
		def apiPath = identityPath ?: "/${tenantId}/oauth2/token"

		def body = [grant_type   : 'client_credentials',
		            resource     : identityResourceUrl,
		            client_id    : clientId,
		            client_secret: clientKey
		]

		def results = callTokenApi(azureUrl, apiPath, [body: body])
		def parsedResults = new groovy.json.JsonSlurper().parseText(results)
		return parsedResults.access_token
	}

	def callApi(path, opts = [:], method = 'POST') {
		def token = opts.token ?: getToken()
		def rtn = [success: false, headers: [:]]
		try {
			def apiUrl = managementUrl
			if(opts.body)
				log.debug("calling apiUrl: ${apiUrl}/${path} with body: ${JsonOutput.prettyPrint(opts.body.encodeAsJson().toString())}")
			else
				log.debug("calling apiUrl: ${apiUrl}/${path} no body")
			URIBuilder uriBuilder = new URIBuilder("${apiUrl}/${path}")
			if(opts.query) {
				opts.query?.each { k, v ->
					uriBuilder.addParameter(k, v)
				}
			}
			def statusCode = 429
			while(statusCode == 429 || statusCode == 302) {
				if(opts.redirectUrl) {
					uriBuilder = new URIBuilder("${opts.redirectUrl}")
				}
				HttpRequestBase request
				switch(method) {
					case 'HEAD':
						request = new HttpHead(uriBuilder.build())
						break
					case 'PUT':
						request = new HttpPut(uriBuilder.build())
						break
					case 'POST':
						request = new HttpPost(uriBuilder.build())
						break
					case 'GET':
						request = new HttpGet(uriBuilder.build())
						break
					case 'DELETE':
						request = new HttpDelete(uriBuilder.build())
						break
					case 'PATCH':
						request = new HttpPatch(uriBuilder.build())
						break
					default:
						throw new Exception('method was not specified')
				}
				if(!opts.redirectUrl) {
					request.addHeader('Authorization', (opts.authType ?: 'Bearer') + ' ' + token)
				}
				// Headers
				if(!opts.headers || !opts.headers['Content-Type']) {
					request.addHeader('Content-Type', 'application/json')
				}
				opts.headers?.each { k, v ->
					request.addHeader(k, v)
				}
				if(opts.body) {
					HttpEntityEnclosingRequestBase postRequest = (HttpEntityEnclosingRequestBase)request
					postRequest.setEntity(new StringEntity(opts.body.encodeAsJson().toString()))
				}
				withClient(opts) { HttpClient client ->
					request.getAllHeaders()?.each { h ->

					}
					CloseableHttpResponse response = client.execute(request)
					try {
						statusCode = response.getStatusLine().getStatusCode()
						if(response.getStatusLine().getStatusCode() == 302) {
							response.getAllHeaders().each { h ->
								rtn.headers["${h.name}"] = h.value
							}
							if(rtn.headers['Location']) {
								opts.redirectUrl = rtn.headers['Location']
								log.debug("Setting redirectUrl: ${opts.redirectUrl}")
							}
							log.debug("Redirect Headers ${rtn.headers}")
						} else if(response.getStatusLine().getStatusCode() <= 300) {
							rtn.success = true
							response.getAllHeaders().each { h ->
								rtn.headers["${h.name}"] = h.value
							}
							HttpEntity entity = response.getEntity()
							if(entity) {
								rtn.content = EntityUtils.toString(entity)
								if(!opts.suppressLog) {
									log.debug "results of SUCCESSFUL call to ${apiUrl}/${path}, results: ${JsonOutput.prettyPrint(rtn.content ?: '')}"
								}
							} else {
								rtn.content = null
							}
							rtn.success = true
						} else if(response.getStatusLine().getStatusCode() == 429) {
							def retryAfter = response.getFirstHeader('Retry-After')?.getValue()?.toLong() ?: 60l
							log.warn("Azure Rate Limit Reached... received Retry After Recommendation of ${retryAfter} seconds. Sleeping...")
							sleep(retryAfter*1000l)
						} else {
							if(response.getEntity()) {
								rtn.content = EntityUtils.toString(response.getEntity())
								if(!opts.suppressLog) {
									log.debug "results of FAILURE call to ${apiUrl}/${path}, results: ${JsonOutput.prettyPrint(rtn.content ?: '')}"
								}
							}
							rtn.success = false
							rtn.errorCode = response.getStatusLine().getStatusCode()?.toString()
							log.warn("path: ${path} error: ${rtn.errorCode} - ${rtn.content}")
						}
					} catch(ex) {
						rtn.success = false
						log.error "Error occurred processing the response for ${apiUrl}", ex
					} finally {
						if(response) {
							response.close()
						}
					}
				}

			}


		} catch (e) {
			rtn.success = false
			log.error("Error Occurred calling Azure management API: ${e.message}",e)
			rtn.error = e.message
		}
		return rtn
	}

	private callTokenApi(apiUrl, path, opts = [:]) {
		log.debug "callTokenApi"

		def content
		if(opts.body && opts.body instanceof String) {
			log.debug("calling callTokenApi: ${apiUrl}/${path} with body: ${JsonOutput.prettyPrint(opts.body)}")
		} else {
			log.debug("calling callTokenApi: ${apiUrl}/${path} no body")
		}

		//build request
		URI uri = new URI("${apiUrl}/${path}")
		HttpRequestBase request = new HttpPost(uri)
		request.addHeader('Content-Type', 'application/x-www-form-urlencoded')
		// Set the request body
		HttpEntityEnclosingRequestBase postRequest = (HttpEntityEnclosingRequestBase)request
		List<NameValuePair> urlParameters = new ArrayList<NameValuePair>()
		opts.body?.each { k, v ->
			urlParameters.add(new BasicNameValuePair(k, v))
		}
		postRequest.setEntity(new UrlEncodedFormEntity(urlParameters))
		withClient([:]) { HttpClient client ->
			CloseableHttpResponse response = client.execute(request)
			try {
				if(response.getStatusLine().getStatusCode() <= 399) {
					HttpEntity entity = response.getEntity()
					content = EntityUtils.toString(entity)
					log.debug "results of SUCCESSFUL call to ${apiUrl}/${path}, results: ${JsonOutput.prettyPrint(content ?: '')}"
				} else {
					content = EntityUtils.toString(response.getEntity())
					log.debug "results of FAILURE call to ${apiUrl}/${path}, results: ${JsonOutput.prettyPrint(content ?: '')}"
					def errorCode = response.getStatusLine().getStatusCode()?.toString()
					log.warn("path: ${path} error: ${errorCode} - ${content}")
					throw new RuntimeException("Error in obtaining Azure token: ErrorCode=${errorCode}, Content=${content}")
				}
			} catch(ex) {
				log.error "Error occurred processing the response for ${apiUrl}", ex
			} finally {
				if(response) {
					response.close()
				}
			}
		}
		content
	}

	private withClient(opts, Closure cl) {
		HttpClientBuilder clientBuilder = HttpClients.custom()
		clientBuilder.setHostnameVerifier(new X509HostnameVerifier() {
			boolean verify(String host, SSLSession sess) {
				return true
			}

			void verify(String host, SSLSocket ssl) {}

			void verify(String host, String[] cns, String[] subjectAlts) {}

			void verify(String host, X509Certificate cert) {}

		})
		if(opts.disableRedirect) {
			clientBuilder.disableRedirectHandling()
		}
		SSLConnectionSocketFactory sslConnectionFactory
		SSLContext sslcontext
		if(ignoreSSL) {
			sslcontext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
				@Override
				boolean isTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
					return true
				}
			}).build()
			sslConnectionFactory = new SSLConnectionSocketFactory(sslcontext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER) {
				@Override
				Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException, ConnectTimeoutException {
					if(socket instanceof SSLSocket) {
						try {
							socket.setEnabledProtocols(['SSLv3', 'TLSv1', 'TLSv1.1', 'TLSv1.2'] as String[])
							log.debug "hostname: ${host?.getHostName()}"
							PropertyUtils.setProperty(socket, "host", host.getHostName())
						} catch (NoSuchMethodException ex) {}
						catch (IllegalAccessException ex) {}
						catch (InvocationTargetException ex) {}
						catch (Exception ex) {
							log.error "We have an unhandled exception when attempting to connect to ${host} ignoring SSL errors", ex
						}
					}
					return super.connectSocket(WEB_CONNECTION_TIMEOUT, socket, host, remoteAddress, localAddress, context)
				}
			}
		} else {
			sslcontext = SSLContexts.createSystemDefault()
			sslConnectionFactory = new SSLConnectionSocketFactory(sslcontext) {
				@Override
				Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException, ConnectTimeoutException {
					if(socket instanceof SSLSocket) {
						try {
							socket.setEnabledProtocols(['SSLv3', 'TLSv1', 'TLSv1.1', 'TLSv1.2'] as String[])
							PropertyUtils.setProperty(socket, "host", host.getHostName())
						} catch(NoSuchMethodException ex) { }
						catch(IllegalAccessException ex) { }
						catch(InvocationTargetException ex) { }
					}
					return super.connectSocket(opts.timeout ?: 90000, socket, host, remoteAddress, localAddress, context)
				}
			}
		}

		HttpMessageParserFactory<HttpResponse> responseParserFactory = new DefaultHttpResponseParserFactory() {

			@Override
			HttpMessageParser<HttpResponse> create(SessionInputBuffer ibuffer, MessageConstraints constraints) {
				LineParser lineParser = new BasicLineParser() {
					@Override
					Header parseHeader(final CharArrayBuffer buffer) {
						try {
							return super.parseHeader(buffer)
						} catch (ParseException ex) {
							return new BasicHeader(buffer.toString(), null)
						}
					}
				}

				return new DefaultHttpResponseParser(ibuffer, lineParser, DefaultHttpResponseFactory.INSTANCE, constraints ?: MessageConstraints.DEFAULT) {
					@Override
					protected boolean reject(final CharArrayBuffer line, int count) {
						//We need to break out of forever head reads
						if(count > 100) {
							return true
						}
						return false
					}
				}
			}
		}
		clientBuilder.setSSLSocketFactory(sslConnectionFactory)
		Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
				.register("https", sslConnectionFactory)
				.register("http", PlainConnectionSocketFactory.INSTANCE)
				.build()
		HttpMessageWriterFactory<HttpRequest> requestWriterFactory = new DefaultHttpRequestWriterFactory()
		HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory = new ManagedHttpClientConnectionFactory(
				requestWriterFactory, responseParserFactory)
		BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(registry, connFactory)
		clientBuilder.setConnectionManager(connectionManager)

		String proxyHost = proxyHost
		Integer proxyPort = proxyPort
		String proxyUser = proxyUser
		String proxyPassword = proxyPassword
		String proxyDomain = proxyDomain
		String proxyWorkstation = proxyWorkstation
		if(proxyHost && proxyPort) {
			log.debug "proxy detected at ${proxyHost}:${proxyPort}"
			clientBuilder.setProxy(new HttpHost(proxyHost, proxyPort))
			if(proxyUser) {
				CredentialsProvider credsProvider = new BasicCredentialsProvider()
				NTCredentials ntCreds = new NTCredentials(proxyUser, proxyPassword, proxyWorkstation, proxyDomain)
				credsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), ntCreds)

				clientBuilder.setDefaultCredentialsProvider(credsProvider)
				clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
			}
		}
		HttpClient client = clientBuilder.build()
		try {
			return cl.call(client)
		} finally {
			connectionManager.shutdown()
		}
	}

	private parseResourceGroupName(String val) {
		def parsedValue = ''
		def startIndex = val.toLowerCase().indexOf('resourcegroups')
		if(startIndex > -1) {
			startIndex = startIndex + 'resourcegroups/'.size()
			def endIndex = val.indexOf('/', startIndex)
			if(endIndex == -1) {
				endIndex = val.size()
			}
			parsedValue = val.substring(startIndex, endIndex)
		}
		parsedValue
	}
}