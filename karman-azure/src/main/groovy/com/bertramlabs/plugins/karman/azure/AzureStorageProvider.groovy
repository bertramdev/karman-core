package com.bertramlabs.plugins.karman.azure

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import groovy.util.XmlSlurper
import groovy.util.logging.Commons
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.HttpRequest
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
import java.text.*;

/**
 * Storage provider implementation for the Azure Storage PageBlob and Container API
 * This is the starting point from which all calls to Azure originate for storing blobs within the Cloud File Containers
 * <p>
 * Below is an example of how this might be initialized.
 * </p>
 * <pre>
 * {@code
 * import com.bertramlabs.plugins.karman.StorageProvider
 * def provider = StorageProvider(
 *  provider: 'azure-pageblob',
 *  storageAccount: 'storage account name',
 *  storageKey: 'storage key'
 * )
 *
 * def blob = provider['container']['example.txt'] 
 * blob.setBytes(byteArray)
 * blob.save()
 * }
 * </pre>
 *
 * @author Bob Whiton
 */
@Commons
public class AzureStorageProvider extends StorageProvider {
	static String providerName = "azure-pageblob"

	String storageAccount
	String storageKey

	public String getProviderName() {
		return providerName
	}

	public String getEndpointUrl() {
		return "https://${storageAccount}.blob.core.windows.net/"
	}

	public String createSignedSignature(opts=[:]) {
		log.info "createSignedSignature: ${opts}"
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

		log.info "signature: ${signature}"

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

	Directory getDirectory(String name) {
		new AzureDirectory(name: name, provider: this)
	}

	List<Directory> getDirectories() {
		def opts = [
			verb: 'GET',
			queryParams: [comp: 'list'], 
			path: '']

		URI listUri
		URIBuilder uriBuilder = new URIBuilder("${getEndpointUrl()}".toString())
		opts.queryParams?.each { k, v ->
			uriBuilder.addParameter(k, v)
		}

		HttpGet request = new HttpGet(uriBuilder.build())
		HttpClient client = prepareRequest(request, opts) 
		HttpResponse response = client.execute(request)

		if(response.statusLine.statusCode != 200) {
			HttpEntity responseEntity = response.getEntity()
			log.error("Error fetching Directory List ${response.statusLine.statusCode}, content: ${responseEntity.content}")
			EntityUtils.consume(response.entity)
			return null
		}

		HttpEntity responseEntity = response.getEntity()
		def xmlDoc = new XmlSlurper().parse(responseEntity.content)
		EntityUtils.consume(response.entity)
		
		def provider = this
		def directories = []
		xmlDoc.Containers?.Container?.each { container ->
			directories << new AzureDirectory(name: container.Name, provider: provider)
		}
		return directories
	}

	protected DefaultHttpClient prepareRequest(HttpRequest request, opts=[:]) {
		if(!opts.headers) {
			opts.headers = [:]
		}

		opts.headers['x-ms-version'] = opts.headers['x-ms-version'] ?: '2015-04-05'
		opts.headers['x-ms-date'] = getDateString()

		def signature = createSignedSignature(opts)
		def authHeader = "SharedKey ${this.storageAccount}:${signature}"
		request.addHeader('Authorization', authHeader)
		opts.headers?.each{entry ->
			if(entry.key != 'Content-Length') {
				request.addHeader(entry.key, entry.value.toString())	
			}
		}
		
		HttpClient client = new DefaultHttpClient()
		HttpParams params = client.getParams()
		HttpConnectionParams.setConnectionTimeout(params, 30000)
		HttpConnectionParams.setSoTimeout(params, 20000)

		return client
	}
}
