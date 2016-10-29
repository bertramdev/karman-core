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

	/**
	 * The Amazon implementation to list all available AMI's via the AWS Java SDK
	 * Note: By default only self owned images will be listed. This can be overridden by specifying the owners Collection
	 * @param options - Filter options for listing images (owner can be specified (defaults to 'self')).
	 * * owner - can be the account id of the account we have execute access to or some special options such as (self, amazon , aws-marketplace , microsoft)
	 * @return resultant virtual images
	 */
	@Override
	Collection<VirtualImageInterface> getVirtualImages(Map options) {
		DescribeImagesRequest imageRequest = new DescribeImagesRequest().withFilters(new LinkedList<Filter>())
		if(options?.public == true) {
			imageRequest.getFilters().add(new Filter().withName("is-public").withValues("true"))
		} else if(options?.public == false) {
			imageRequest.getFilters().add(new Filter().withName("is-public").withValues("false"))
		}

		if(options?.owners != null) {
			imageRequest.setOwners(options.owners)
		} else {
			imageRequest.setOwners(['self'])
		}

		List<Image> awsImages = getEC2Client().describeImages(imageRequest).getImages()
		return awsImages?.collect{ new AWSVirtualImage(this, it)}
	}

	@Override
	Collection<VirtualImageInterface> getVirtualImages() {
		return getVirtualImages([:])
	}

	@Override
	VirtualImageInterface getVirtualImage(String uid) {
		DescribeImagesRequest imageRequest = new DescribeImagesRequest().withImageIds(new LinkedList<String>())
		imageRequest.getImageIds().add(uid)
		List<Image> awsImages = getEC2Client().describeImages(imageRequest).getImages()
		if(awsImages.size() > 0) {
			return new AWSVirtualImage(this,awsImages[0])
		}
		return null
	}

	@Override
	VirtualImageInterface createVirtualImage(String name) {
		return new AWSVirtualImage(this,name);
	}
}
