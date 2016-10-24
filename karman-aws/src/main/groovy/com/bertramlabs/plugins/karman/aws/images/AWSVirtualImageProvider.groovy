package com.bertramlabs.plugins.karman.aws.images

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Image
import com.bertramlabs.plugins.karman.images.VirtualImageInterface
import com.bertramlabs.plugins.karman.images.VirtualImageProvider

/**
 * Created by davydotcom on 10/24/16.
 */
class AWSVirtualImageProvider extends VirtualImageProvider{
	static String providerName = 'aws'

	String accessKey = ''
	String secretKey = ''
	String region = ''
	String endpoint = ''
	String protocol = 'https'
	String proxyHost
	String proxyPort
	String proxyUser
	String proxyPassword
	Integer maxConnections = 50
	Boolean keepAlive = false
	AmazonEC2Client client

	public AWSVirtualImageStorageProvider(Map options) {
		accessKey      = options.accessKey      ?: accessKey
		secretKey      = options.secretKey      ?: secretKey
		region         = options.region         ?: region
		endpoint       = options.endpoint       ?: endpoint
		protocol       = options.protocol       ?: protocol
		maxConnections = options.maxConnections ?: maxConnections
		keepAlive      = options.keepAlive      ?: keepAlive
		proxyHost = options.proxyHost ?: proxyHost
		proxyPort = options.proxyPort ?: proxyPort
		proxyUser = options.proxyUser ?: proxyUser
		proxyPassword = options.proxyPassword ?: proxyPassword
	}

	AmazonEC2Client getEC2Client() {
		if(client) {
			return client
		}
		if (accessKey && secretKey) {
			BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey)
			ClientConfiguration configuration = new ClientConfiguration()
			configuration.setUseTcpKeepAlive(keepAlive)
			configuration.setMaxConnections(maxConnections)
			configuration.setProtocol(protocol == 'https' ? com.amazonaws.Protocol.HTTPS : com.amazonaws.Protocol.HTTP)
			client = new AmazonEC2Client(credentials, configuration)
			if (region) {
				Region region = RegionUtils.getRegion(region)
				client.region = region
			}
			if (endpoint) {
				client.endpoint = endpoint
			}
		} else {
			return null
		}
		client
	}


	@Override
	Collection<VirtualImageInterface> getVirtualImages(Map options) {
		DescribeImagesRequest imageRequest = new DescribeImagesRequest().withFilters(new LinkedList<Filter>())
		imageRequest.getFilters().add(new Filter().withName("is-public").withValues("false"))
		List<Image> awsImages = getEC2Client().describeImages(imageRequest).getImages()
		return awsImages?.collect{ new AWSVirtualImage(it)}
	}

	@Override
	Collection<VirtualImageInterface> getVirtualImages() {
		return null
	}

	@Override
	VirtualImageInterface getVirtualImage(String uid) {
		return null
	}

	@Override
	VirtualImageInterface createVirtualImage(String name) {
		return null
	}
}
