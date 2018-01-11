package com.bertramlabs.plugins.karman.alibaba

import com.aliyun.oss.OSSClient
import com.aliyun.oss.model.Bucket
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider

class AlibabaStorageProvider extends StorageProvider {

	static String providerName = "alibaba"

	String accessKey = ''
	String secretKey = ''
	String region = 'cn-hangzhou'
	String baseUrl
	Map<String,String> baseUrls
	String protocol = 'https'
	String proxyHost
	Integer proxyPort
	String endpoint
	String proxyUser
	String proxyPassword
	String proxyWorkstation
	String proxyDomain
	Integer maxConnections = 50
	Boolean keepAlive = false
	Boolean useGzip = false
	Boolean forceMultipart = false
	OSSClient client = null
	Long chunkSize = 100l*1024l*1024l
	public AlibabaStorageProvider(Map options) {
		endpoint       = options.endpoint       ?: endpoint
		accessKey      = options.accessKey      ?: accessKey
		secretKey      = options.secretKey      ?: secretKey
		region         = options.region         ?: region
		protocol       = options.protocol       ?: protocol
		maxConnections = options.maxConnections ?: maxConnections
		keepAlive      = options.keepAlive      ?: keepAlive
		defaultFileACL = options.defaultFileACL ?: defaultFileACL
		useGzip        = options.useGzip        ?: useGzip
		forceMultipart = options.forceMultipart ?: forceMultipart

		baseUrl = options.baseUrl ?: baseUrl
		baseUrls = options.baseUrls ?: baseUrls
		proxyHost = options.proxyHost ?: proxyHost
		proxyPort = options.proxyPort ?: proxyPort
		proxyUser = options.proxyUser ?: proxyUser
		proxyPassword = options.proxyPassword ?: proxyPassword
		proxyDomain = options.proxyDomain ?: proxyDomain
		proxyWorkstation = options.proxyWorkstation ?: proxyWorkstation
		chunkSize = options.chunkSize ?: chunkSize
		tempDir = options.tempDir ?: tempDir
	}

	Directory getDirectory(String name) {
		new AlibabaDirectory(name: name, provider: this)
	}

	List<Directory> getDirectories() {
		List<Bucket> buckets = getOSSClient().listBuckets()
		buckets.collect { bucket -> directoryFromOSSBucket(bucket)}
	}

	OSSClient getOSSClient() {
		if(client) {
			return client
		}
		if(endpoint) {
			client = new OSSClient(endpoint,accessKey,secretKey)
		} else if(region) {
			client = new OSSClient("${protocol ?: 'https'}://oss-${region}.aliyuncs.com",accessKey,secretKey)
		}

		return client
	}



	// PRIVATE

	private AlibabaDirectory directoryFromOSSBucket(bucket) {
		new AlibabaDirectory(name: bucket.name, provider: this)
	}

}