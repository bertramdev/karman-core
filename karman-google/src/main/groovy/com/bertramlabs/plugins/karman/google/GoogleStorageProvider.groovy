/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bertramlabs.plugins.karman.google

import groovy.util.logging.Commons

import org.apache.http.client.config.RequestConfig
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import com.google.api.services.storage.*
import com.google.auth.oauth2.*
import groovy.json.JsonSlurper
import org.apache.http.config.*
import org.apache.http.impl.client.HttpClients
import org.apache.commons.beanutils.PropertyUtils
import org.apache.http.*
import org.apache.http.auth.AuthScope
import org.apache.http.auth.NTCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.*
import org.apache.http.client.utils.URIBuilder
import org.apache.http.config.MessageConstraints
import org.apache.http.config.Registry
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.HttpConnectionFactory
import org.apache.http.conn.ManagedHttpClientConnection
import org.apache.http.impl.DefaultHttpResponseFactory
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.SSLContextBuilder
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.conn.ssl.X509HostnameVerifier
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.InputStreamEntity
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
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
import org.apache.http.message.LineParser
import org.apache.http.protocol.HttpContext
import org.apache.http.util.CharArrayBuffer
import org.apache.http.util.EntityUtils

import javax.net.ssl.*
import java.lang.reflect.InvocationTargetException
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.zip.GZIPInputStream

@Commons
class GoogleStorageProvider extends StorageProvider {

	static String providerName = "google"

	static Integer WEB_CONNECTION_TIMEOUT = 60 * 1000

	String clientEmail
	String privateKey
	String projectId

	String proxyHost
	Integer proxyPort
	String proxyUser
	String proxyPassword
	String proxyWorkstation
	String proxyDomain

	Long chunkSize = 0l

	String getProviderName() {
		return this.providerName
	}

	public GoogleStorageProvider(Map options) {
		clientEmail = options.clientEmail ?: clientEmail
		privateKey  = options.privateKey  ?: privateKey
		projectId = options.projectId ?: projectId
	}

	Directory getDirectory(String name) {
		new GoogleCloudBucket(name: name, provider: this)
	}

	def getDirectories() {
		log.debug "getDirectories"

		def path = "storage/v1/b"
		def keepGoing = true
		def nextPageToken
		def directories = []
		def provider = this
		while(keepGoing) {
			def requestOpts = [query: [project: projectId, maxResults: 1000, pageToken: nextPageToken, projection: 'noAcl']]
			def results = callApi("https://storage.googleapis.com", path, requestOpts, 'GET')
			if(results.success) {
				for(bucket in results.data.items) {
					directories << new GoogleCloudBucket(name: bucket.name, provider: provider, storageClass: bucket.storageClass, locationType:bucket.locationType, location: bucket.location, metaDataLoaded: true)
				}
				nextPageToken = results.data.nextPageToken
				if(!nextPageToken) {
					keepGoing = false
				}
			} else {
				throw new Exception("Error in calling google api: ${parseRestError(results)}") 
			}
		}
		directories
	}

	def callApi(String url, String path, Map opts = [:], String method = 'POST') {
		log.debug "callApi url:${url} path:${path}"
		def rtn = [success:false, headers:[:], cookies:[:]]
		try {
			if(!opts.headers) {
				opts.headers = [:]
			}
			if(!opts.headers.Authorization) {
				opts.headers += getAuthHeaders()
			}
			if(!opts.proxySettings) {
				opts.proxySettings = getProxySettings()
			}
			def expectJson = !opts.inputStream
			URIBuilder uriBuilder = new URIBuilder("${url}")
			if(path) {
				if(opts.additionalPathSegments) {
					def segments = path.tokenize('/') + opts.additionalPathSegments
					uriBuilder.setPathSegments(segments)
				} else {
					uriBuilder.setPath(path)
				}
			}
			if (opts.query) {
				opts.query?.each { k, v ->
					uriBuilder.addParameter(k, v?.toString())
				}
			}
			def uri = uriBuilder.build()
			
			//get the request
			HttpRequestBase request
			switch(method) {
				case 'HEAD':
					request = new HttpHead(uri)
					break
				case 'PUT':
					request = new HttpPut(uri)
					break
				case 'POST':
					request = new HttpPost(uri)
					break
				case 'GET':
					request = new HttpGet(uri)
					break
				case 'DELETE':
					request = new HttpDelete(uri)
					break
				case 'PATCH':
					request = new HttpPatch(uri)
					break
				default:
					throw new Exception('method was not specified')
			}

			//add headers
			if(!opts.headers || !opts.headers['Content-Type']) {
				request.addHeader('Content-Type', 'application/json')
			}
			opts.headers?.each { k, v ->
				request.addHeader(k, v)
			}

			//set the body
			if(opts.body) {
				HttpEntityEnclosingRequestBase postRequest = (HttpEntityEnclosingRequestBase)request
				if(opts.body instanceof Map) {
					if(opts.bodyType == 'multi-part-form') {
						def entityBuilder = MultipartEntityBuilder.create()
						def rowBoundary = '--' + java.util.UUID.randomUUID().toString() + '--'
						opts.body?.each { k, v ->
							//if multiples..
							if(v instanceof Collection) {
								v.each { rowValue ->
									def rowBody = new StringBody(rowValue.toString(), ContentType.create('text/plain', 'UTF-8'))
    							entityBuilder.addPart(k, rowBody)	
								}
							} else {
								def rowValue
								//convert it
								if(v instanceof CharSequence) {
									rowValue = v
								} else {
									rowValue = v.toString()
								}
	                            def rowBody = new StringBody(rowValue, ContentType.create('text/plain', 'UTF-8'))
	                            entityBuilder.addPart(k, rowBody)
							}
						}
						entityBuilder.setContentType(ContentType.MULTIPART_FORM_DATA)
						entityBuilder.setBoundary(rowBoundary)
						postRequest.setEntity(entityBuilder.build())
						//replace the header
						if(request.containsHeader('Content-Type')) {
							//append the boundary
							def currentType = request.getFirstHeader('Content-Type')
							def newValue = currentType.getValue()
							newValue = newValue + '; boundary=' +  rowBoundary
							request.setHeader('Content-Type', newValue)
						}
					} else if (opts.body.type == 'inputStream') {
						expectJson = false
						//postRequest.setEntity(new InputStreamEntity(opts.body.inputStream, opts.body.contentLength))
						if(opts.chunked) {
							postRequest.setEntity(new InputStreamEntity(opts.body.inputStream, -1))
						} else {
							postRequest.setEntity(new InputStreamEntity(opts.body.inputStream))
						}
					} else {
						def json = new groovy.json.JsonBuilder(opts.body)
						postRequest.setEntity(new StringEntity(json.toString()))
					}
				} else if(opts.body instanceof byte[]) {
					postRequest.setEntity(new ByteArrayEntity(opts.body))
				} else {
					postRequest.setEntity(new StringEntity(opts.body))
				}
			}
			//make the call
			withClient(opts) { HttpClient client ->
				CloseableHttpResponse response = client.execute(request)
				try {
					rtn.statusCode = response.getStatusLine().getStatusCode()
					if(response.getStatusLine().getStatusCode() <= 399) {
						response.getAllHeaders().each { h ->
							rtn.headers["${h.name}"] = h.value
							if(h.name == 'Set-Cookie') {
								def dataStart = h.value.indexOf('=')
								def dataEnd = h.value.indexOf(';')
								if(dataStart > -1 && dataEnd > -1) {
									def cookieRow = [:]
									def cookieName = h.value.substring(0, dataStart)
									cookieRow.value = h.value.substring(dataStart + 1, dataEnd)
									//extra info
									def cookieConfig = h.value?.tokenize(';')	
									if(cookieConfig?.size() > 1) {
										//add extras
										cookieConfig.each { cookieOption ->
											def cookieTokens = cookieOption.tokenize('=')
											def optionName = cookieTokens?.size() > 0 ? cookieTokens[0]?.trim() : null
											if(cookieTokens?.size() > 1) {
												if(optionName != cookieName)
													cookieRow[optionName] = cookieTokens[1]
											} else if(cookieTokens?.size() > 0) {
												cookieRow[optionName] = true
											}
										}
									}
									rtn.cookies[cookieName] = cookieRow
								}
							}
						}
						if(response.containsHeader("Location")) {
							rtn.location = response.getFirstHeader("Location").value
						}
						HttpEntity entity = response.getEntity()
						if(entity) {
							if(opts.stream == true && opts.streamProcessor) {
								//process the stream
								opts.streamProcessor.call(response)
							} else {
								if(response.containsHeader("Content-Type") && response.getFirstHeader("Content-Type").value == 'application/x-gzip') {
									GZIPInputStream gzipIs = new GZIPInputStream(response.entity.getContent())
									rtn.content = gzipIs.text
								} else if(opts.inputStream) {
									rtn.content = entity.content
								} else {
									rtn.content = EntityUtils.toString(entity);
								}
							}
						} else {
							rtn.content = null
						}
						if(opts.reuse) {
							rtn.httpClient = client	
						}
						rtn.success = true
					} else {
						if(response.getEntity()) {
							rtn.content = EntityUtils.toString(response.getEntity());
						}
						rtn.success = false
						rtn.errorCode = response.getStatusLine().getStatusCode()
						log.warn("Failure in call ${url} path: ${path} error: ${rtn.errorCode} - ${rtn.content}")
					}
				} catch(ex) {
					log.error "Error occurred processing the response for ${url}/${path} : ${ex.message}", ex
					rtn.error = "Error occurred processing the response for ${url}/${path} : ${ex.message}"
					rtn.success = false
				} finally {
					if(response && !opts.inputStream) {
						response.close()
					}
				}
			}
		} catch(javax.net.ssl.SSLProtocolException sslEx) {
			log.error("Error Occurred calling web API (SSL Exception): ${sslEx.message}", sslEx)
			rtn.error = "SSL Handshake Exception (is SNI Misconfigured): ${sslEx.message}"
			rtn.success = false	
		} catch(SocketTimeoutException e) {
			log.error("Error Occurred calling web API: ${url} ${path} - ${e.message}")
			rtn.error = e.message
			rtn.success = false
		} catch(Exception e) {
			log.error("Error Occurred calling web API: ${url} ${path} - ${e.message}", e)
			rtn.error = e.message
			rtn.success = false
		}

		rtn.data = [:]
		if(rtn.statusCode != 404 && expectJson && rtn.content instanceof String && rtn.content?.length() > 0) {
			try {
				rtn.data = new JsonSlurper().parseText(rtn.content)
			} catch(e) {
				log.debug("Error parsing API response JSON: ${e}", e)
			}
		}
		return rtn
	}

	private withClient(opts, Closure cl) {
		if(opts.httpClient) {
			try {
				return cl.call(opts.httpClient)
			} finally {
				if(!opts.reuse) {
					opts.httpClient.connectionManager.shutdown()
				}
			}
		}
		def ignoreSSL = (opts.ignoreSSL == null || opts.ignoreSSL == true)
		HttpClientBuilder clientBuilder = HttpClients.custom()
		if(opts.noRedirects) {
			log.info("disable redirects")
			clientBuilder.disableRedirectHandling()
		}
		if(opts.connectionTimeout || opts.readTimeout) {
			def reqConfigBuilder = RequestConfig.custom()
			if(opts.connectTimeout) {
				reqConfigBuilder.setConnectTimeout(opts.connectTimeout)
				reqConfigBuilder.setConnectionRequestTimeout(opts.connectTimeout)
			}
			if(opts.readTimeout) {
				reqConfigBuilder.setSocketTimeout(opts.readTimeout)
			}
			clientBuilder.setDefaultRequestConfig(reqConfigBuilder.build())
		}
		clientBuilder.setHostnameVerifier(new X509HostnameVerifier() {
			public boolean verify(String host, SSLSession sess) { return true }
			public void verify(String host, SSLSocket ssl) {}
			public void verify(String host, String[] cns, String[] subjectAlts) {}
			public void verify(String host, X509Certificate cert) {}
		})
		SSLConnectionSocketFactory sslConnectionFactory
		SSLContext sslcontext
		if(ignoreSSL) {
			if(opts.cert && opts.privateKey) {
				// certs are being used
				KeyStore keystore = createKeyStore(opts.privateKey, opts.cert);
				sslcontext = new SSLContextBuilder().loadKeyMaterial(keystore, ''.toCharArray()).loadTrustMaterial(null, new TrustStrategy() {
					@Override
					boolean isTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
						return true
					}
				}).build()
			} else {
				sslcontext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
					@Override
					boolean isTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
						return true
					}
				}).build()
			}
			sslConnectionFactory = new SSLConnectionSocketFactory(sslcontext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER) {
				@Override 
				protected void prepareSocket(SSLSocket socket) {
					if(opts.ignoreSSL) {
						PropertyUtils.setProperty(socket, "host", null);
						List<SNIServerName> serverNames  = Collections.<SNIServerName> emptyList();
						SSLParameters sslParams = socket.getSSLParameters();
						sslParams.setServerNames(serverNames);
						socket.setSSLParameters(sslParams);
					}
				}
				@Override
				public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) 
						throws IOException, ConnectTimeoutException {
					if(socket instanceof SSLSocket) {
						try {
							socket.setEnabledProtocols(['SSLv3', 'TLSv1', 'TLSv1.1', 'TLSv1.2'] as String[])
							SSLSocket sslSocket = (SSLSocket)socket

							log.debug "hostname: ${host?.getHostName()}"
							PropertyUtils.setProperty(socket, "host", host.getHostName());	
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
			if(opts.cert && opts.privateKey) {
				// certs are being used
				// TODO : Handle cacert
				KeyStore keystore = createKeyStore(opts.privateKey, opts.cert);
				sslcontext = new SSLContextBuilder().loadKeyMaterial(keystore, ''.toCharArray()).build()
			} else {
				sslcontext = SSLContexts.createSystemDefault()
			}
			sslConnectionFactory = new SSLConnectionSocketFactory(sslcontext) {
				@Override
				public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) 
						throws IOException, ConnectTimeoutException {
					if(socket instanceof SSLSocket) {
						try {
							socket.setEnabledProtocols(['SSLv3', 'TLSv1', 'TLSv1.1', 'TLSv1.2'] as String[])
							PropertyUtils.setProperty(socket, "host", host.getHostName());
						} catch(NoSuchMethodException ex) {}
						catch(IllegalAccessException ex) {}
						catch(InvocationTargetException ex) {}
					}
					return super.connectSocket(opts.timeout ?: 30000, socket, host, remoteAddress, localAddress, context)
				}
			}
		}
		HttpMessageParserFactory<HttpResponse> responseParserFactory = new DefaultHttpResponseParserFactory() {
			@Override
			public HttpMessageParser<HttpResponse> create(SessionInputBuffer ibuffer, MessageConstraints constraints) {
				LineParser lineParser = new BasicLineParser() {
					@Override
					public Header parseHeader(final CharArrayBuffer buffer) {
						try {
							return super.parseHeader(buffer);
						} catch (ParseException ex) {
							return new BasicHeader(buffer.toString(), null);
						}
					}
				};
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
				.build();
		HttpMessageWriterFactory<HttpRequest> requestWriterFactory = new DefaultHttpRequestWriterFactory();
		HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory = new ManagedHttpClientConnectionFactory(
				requestWriterFactory, responseParserFactory);
		BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(registry, connFactory)
		clientBuilder.setConnectionManager(connectionManager)
		if(opts.proxySettings) {
			def proxySettings = opts.proxySettings
			def proxyHost = proxySettings.proxyHost
			def proxyPort = proxySettings.proxyPort
			if(proxyHost && proxyPort) {
				log.debug "proxy detected at ${proxyHost}:${proxyPort}"
				def proxyUser = proxySettings.proxyUser
				def proxyPassword = proxySettings.proxyPassword
				def proxyWorkstation = proxySettings.proxyWorkstation ?: null
				def proxyDomain = proxySettings.proxyDomain ?: null
				clientBuilder.setProxy(new HttpHost(proxyHost, proxyPort))
				if(proxyUser) {
					CredentialsProvider credsProvider = new BasicCredentialsProvider()
					NTCredentials ntCreds = new NTCredentials(proxyUser, proxyPassword, proxyWorkstation, proxyDomain)
					credsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), ntCreds)
					clientBuilder.setDefaultCredentialsProvider(credsProvider)
					clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
				}
			}
		}
		HttpClient client = clientBuilder.build()
		try {
			return cl.call(client)
		} finally {
			if(!opts.reuse && !opts.inputStream) {
				connectionManager.shutdown()
			}
		}
	}

	def parseRestError(jsonResults) {
		log.error "parseRestError ${jsonResults}"
		def err = [msg: null]
		if(jsonResults.content) {
			try {
				def content = new groovy.json.JsonSlurper().parseText(jsonResults.content)
				err.msg = content?.error?.message ?: ''
				err.errorCode = content?.error?.code
				if(content?.error?.details?.violations) {
					content?.error?.details?.violations?.each {
						err.msg += it.description
					}
				}
			} catch(e) {
			}
		}
		err.msg = err.msg ?: "Error calling Google"
		return err
	}

	private getProxySettings() {
		if(proxyHost) {
			return [
				proxyHost: proxyHost,
				proxyPort: proxyPort,
				proxyUser: proxyUser,
				proxyPassword: proxyPassword,
				proxyDomain: proxyDomain,
				proxyWorkstation: proxyWorkstation
			]
		}
		return null
	}

	private getAuthHeaders() {
		log.debug "getAuthHeaders"

String credentialsString = """
{
  "type": "service_account",
  "project_id": "${projectId}",
  "private_key_id": "",
  "private_key": "${privateKey?.replace('\r','\\r')?.replace('\n','\\n')}",
  "client_email": "${clientEmail}",
  "client_id": "",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://accounts.google.com/o/oauth2/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs"
}
"""
		InputStream is = new ByteArrayInputStream(credentialsString.getBytes())
		def credentials = ServiceAccountCredentials.fromStream(is).createScoped(StorageScopes.all())

		def uri = new java.net.URI("https://storage.googleapis.com")
		def credentialHeaders = credentials.getRequestMetadata(uri);
		[Authorization: credentialHeaders['Authorization'].getAt(0)]
	}

}