package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.network.NetworkProvider
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Commons
import org.apache.http.client.methods.*
import org.apache.http.client.methods.HttpPost
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
import org.apache.http.client.methods.HttpPut
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
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.ProxyAuthenticationStrategy
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
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

import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import java.lang.reflect.InvocationTargetException
import java.security.cert.X509Certificate

import javax.net.ssl.SSLContext

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
 *}
 * </pre>
 *
 * @author David Estes
 * @author Bob Whiton
 * @author Dustin DeYoung
 */
@Commons
public class OpenstackNetworkProvider extends NetworkProvider {

	static String providerName = "openstack"

	OpenstackApiClient apiClient
	Map configOptions

	/**
	 * Constructor for the OpenstackNetworkProvider
	 * @param config Map of configuration options for the provider
	 */
	def OpenstackNetworkProvider(Map config) {
		super(config)
		this.apiClient = new OpenstackApiClient(config)
		this.configOptions = config
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

	@Override
	Collection<SecurityGroupInterface> getSecurityGroups(Map options) {
		return this.getSecurityGroups()
	}

	@Override
	Collection<SecurityGroupInterface> getSecurityGroups() {
		def apiEndpoint = apiClient.getApiEndpoint('network')
		def apiVersion  = apiClient.getApiEndpointVersion('network')
		if(apiEndpoint) {
			def result = apiClient.callApi(apiEndpoint, "/${apiVersion}/security-groups", [query: [tenant_id: apiClient.projectId]])
			if(!result.success) {
				throw new RuntimeException("Error in obtaining security groups: ${result.error}")
			}
			def provider = this
			return result.content?.security_groups?.collect { securityGroupMeta ->
				return new OpenstackSecurityGroup(provider, securityGroupMeta)
			}
		} else {
			throw new RuntimeException("Could not get security groups: no network api configured")
		}
	}

	@Override
	SecurityGroupInterface getSecurityGroup(String uid) {
		def apiEndpoint = apiClient.getApiEndpoint('network')
		def apiVersion  = apiClient.getApiEndpointVersion('network')

		if(apiEndpoint) {
			def result = apiClient.callApi(apiEndpoint, "/${apiVersion}/security-groups/${uid}", [query: [tenant_id: apiClient.projectId]])
			if(!result.success) {
				throw new RuntimeException("Error in obtaining security group: ${result.error}")
			}
			return new OpenstackSecurityGroup(this, result.content?.security_group)
		} else {
			throw new RuntimeException("Could not get security group: no network api configured")
		}
	}

	@Override
	SecurityGroupInterface createSecurityGroup(String name) {
		return new OpenstackSecurityGroup(this, [name: name])
	}

}
