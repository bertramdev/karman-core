package com.bertramlabs.plugins.karman.azure

import com.bertramlabs.plugins.karman.DirectoryInterface

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import groovy.util.XmlSlurper
import groovy.util.logging.Slf4j
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.ParseException
import org.apache.http.auth.AuthScope
import org.apache.http.auth.NTCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPut
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
import org.apache.http.conn.ssl.X509HostnameVerifier
import org.apache.http.conn.ssl.SSLContextBuilder
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.entity.InputStreamEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.DefaultHttpResponseFactory
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.config.RequestConfig
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
import org.apache.http.message.LineParser
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.CharArrayBuffer
import org.apache.http.util.EntityUtils

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.lang.reflect.InvocationTargetException
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate

import java.text.*;

@Slf4j
abstract class AzureStorageProvider<T extends DirectoryInterface> extends StorageProvider<T> {

	String storageAccount
	String storageKey
	String proxyHost
	Integer proxyPort
	String proxyUser
	String proxyPassword
	String proxyWorkstation
	String proxyDomain
	String noProxy
	String protocol = 'https'
	String baseEndpointDomain

	abstract String getEndpointUrl()

	public String createSignedSignature(opts=[:]) {
		log.debug "createSignedSignature: ${opts}"
		// Create the canonical header
		def msHeadersMap = opts.headers?.findAll { k, v -> k.startsWith('x-ms-') }?.sort()
		def msHeaderParams = []
		msHeadersMap?.each { k,v ->
    		msHeaderParams << "${k.toLowerCase().trim()}:${v?.toString()?.replace('\n',' ')}"
		}
		def canonicalizedHeaders = msHeaderParams.join('\n')

		// Create the canonical resource
		def canonicalizedResource = "/${storageAccount}/"
		if(opts.path) {
			canonicalizedResource += "${opts.path}\n"
		} else {
			canonicalizedResource += "\n"
		}
		if(opts.queryParams) {
			def queryParams = []
			def queryParamsMap = opts.queryParams.sort()
			queryParamsMap?.each { k, v ->
				queryParams << "${k.toLowerCase()}:${v}"
			}
			canonicalizedResource += queryParams.join('\n')
		}

		if(canonicalizedResource.endsWith('\n')) {
			canonicalizedResource = canonicalizedResource[0..-2]
		}
		
		def signParams = [] 
		signParams << "${opts.verb.toUpperCase()}"
		signParams << "${opts.headers['Content-Encoding'] ?: ''}"
		signParams << "${opts.headers['Content-Language'] ?: ''}"
		signParams << "${opts.headers['Content-Length'] == 0 ? '' : (opts.headers['Content-Length'] ?: '')}"
		signParams << "${opts.headers['Content-MD5'] ?: ''}"
		signParams << "${opts.headers['Content-Type'] ?: ''}"
		signParams << "" // Ignore the date portion because it is in the header
		signParams << "${opts.headers['If-Modified-Since'] ?: ''}"
		signParams << "${opts.headers['If-Match'] ?: ''}"
		signParams << "${opts.headers['If-None-Match'] ?: ''}"
		signParams << "${opts.headers['If-Unmodified-Since'] ?: ''}"
		signParams << "${opts.headers['Range'] ?: ''}"
		signParams << "${canonicalizedHeaders}"
		signParams << "${canonicalizedResource}"

		def signature = signParams.join('\n')

		log.debug "signature: ${signature}"

		// Sign and encode it
		Mac mac = Mac.getInstance("HmacSHA256")
		SecretKeySpec secretKeySpec = new SecretKeySpec(this.storageKey.toString().decodeBase64(), "HmacSHA256")
		mac.init(secretKeySpec)
		byte[] digest = mac.doFinal(signature.toString().getBytes('UTF-8'))
		def encodedSignature = digest.encodeBase64().toString()

		return encodedSignature
	}

	private String getDateString() {
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return dateFormat.format(calendar.getTime());
	}

	protected prepareRequest(opts) {
		URIBuilder uriBuilder = new URIBuilder(opts.uri)
		if(opts.queryParams) {
			opts.queryParams?.each { k, v ->
				uriBuilder.addParameter(k, v)
			}
		}

		def request
		def uri = uriBuilder.build()
		switch(opts.verb) {
			case 'HEAD':
				request = new HttpHead(uri)
				break
			case 'PUT':
				request = new HttpPut(uri)
				break
			case 'GET':
				request = new HttpGet(uri)
				break
			case 'DELETE':
				request = new HttpDelete(uri)
				break
			default:
				throw new Exception('verb was not specified')
		}
		
		if(!opts.headers) {
			opts.headers = [:]
		}

		opts.headers['x-ms-version'] = opts.headers['x-ms-version'] ?: '2015-04-05'
		opts.headers['x-ms-date'] = getDateString()
		def targetPath
//		opts.path = uri.path ? (uri.path?.startsWith('/') ? uri.path.substring(1) :  uri.path) : ''

		def signature = createSignedSignature(opts)
		def authHeader = "SharedKey ${this.storageAccount}:${signature}"
		request.addHeader('Authorization', authHeader)
		opts.headers?.each{entry ->
			if(entry.key != 'Content-Length') {
				request.addHeader(entry.key, entry.value.toString())	
			}
		}
		
		HttpClientBuilder clientBuilder = HttpClients.custom()
		clientBuilder.setHostnameVerifier(new X509HostnameVerifier() {
			public boolean verify(String host, SSLSession sess) {
				return true
			}

			public void verify(String host, SSLSocket ssl) {

			}

			public void verify(String host, String[] cns, String[] subjectAlts) {

			}

			public void verify(String host, X509Certificate cert) {

			}

		})

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
						log.debug "hostname: ${host?.getHostName()}"
						PropertyUtils.setProperty(socket, "host", host.getHostName());
					} catch (NoSuchMethodException ex) {}
					catch (IllegalAccessException ex) {}
					catch (InvocationTargetException ex) {}
					catch (Exception ex) {
						log.error "We have an unhandled exception when attempting to connect to ${host} ignoring SSL errors", ex
					}
				}
				return super.connectSocket(20 * 1000, socket, host, remoteAddress, localAddress, context)
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
				return new DefaultHttpResponseParser(
					ibuffer, lineParser, DefaultHttpResponseFactory.INSTANCE, constraints ?: MessageConstraints.DEFAULT) {

					@Override
					protected boolean reject(final CharArrayBuffer line, int count) {
						//We need to break out of forever head reads
						if(count > 100) {
							return true
						}
						return false;

					}

				};
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

		HttpClient client = clientBuilder.build()

		return [client, request]
	}

	protected throwResponseFailure(response, message) {
		HttpEntity responseEntity = response.getEntity()
		def xmlDoc = new XmlSlurper().parse(responseEntity.content)
		EntityUtils.consume(response.entity)
		def errMessage = "${message}: ${xmlDoc.Message}"
		log.error errMessage
		log.error xmlDoc
		throw new Exception(errMessage)
	}
}
