package com.bertramlabs.plugins.karman.aws.network

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.SecurityGroup
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
 *  provider: 'aws',
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
public class AWSNetworkProvider extends NetworkProvider {
	static String providerName = "aws"

	String accessKey = ''
	String secretKey = ''
	String region = ''
	String endpoint = ''
	String protocol = 'https'
	String proxyHost
	String proxyPort
	String proxyUser
	String vpc
	String proxyPassword
	Integer maxConnections = 50
	Boolean keepAlive = false
	AmazonEC2Client client

	public AWSNetworkProvider(Map options) {
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
		vpc = options.vpc ?: vpc
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
	public String getProviderName() {
		return OpenstackNetworkProvider.providerName
	}

	@Override
	public Collection<SecurityGroupInterface> getSecurityGroups(Map options) {
		DescribeSecurityGroupsRequest securityRequest = new DescribeSecurityGroupsRequest().withFilters(new LinkedList<Filter>())
		if(vpc) {
			securityRequest.getFilters().add(new Filter().withName("vpc-id").withValues(vpc))
		}
		List<SecurityGroup> awsSecurityGroups = getEC2Client().describeSecurityGroups(securityRequest).getSecurityGroups()

		return awsSecurityGroups?.collect{ new AWSSecurityGroup(this, it)}

	}

	@Override
	public Collection<SecurityGroupInterface> getSecurityGroups() {
		return this.getSecurityGroups(null)
	}

	@Override
	public SecurityGroupInterface getSecurityGroup(String uid) {
		DescribeSecurityGroupsRequest securityRequest = new DescribeSecurityGroupsRequest().withFilters(new LinkedList<Filter>())
		securityRequest.getGroupIds().add(uid)
		if(vpc) {
			securityRequest.getFilters().add(new Filter().withName("vpc-id").withValues(vpc))
		}
		List<SecurityGroup> awsSecurityGroups = getEC2Client().describeSecurityGroups(securityRequest).getSecurityGroups()
		if(awsSecurityGroups.size() > 0) {
			return new AWSSecurityGroup(this, awsSecurityGroups[0])
		} else {
			return null
		}
	}

	@Override
	public SecurityGroupInterface createSecurityGroup(String name) {
		return new AWSSecurityGroup(this, [name: name])
	}

}

