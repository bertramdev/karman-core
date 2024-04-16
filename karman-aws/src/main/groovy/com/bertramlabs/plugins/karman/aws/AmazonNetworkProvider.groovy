package com.bertramlabs.plugins.karman.aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.services.securitytoken.model.AssumeRoleResult
import com.bertramlabs.plugins.karman.network.NetworkProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.RegionUtils
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import groovy.util.logging.Commons
import com.amazonaws.regions.RegionUtils

@Commons
class AmazonNetworkProvider extends NetworkProvider {
	static String providerName = "amazon"

	String accessKey
	String secretKey
	Boolean useHostCredentials = false
	String stsAssumeRole
	String stsExternalId = null
	String token
	String proxyHost
	Integer proxyPort
	String proxyUser
	String proxyPassword
	String proxyWorkstation
	String proxyDomain
	String noProxy
	String endpoint
	String region
	private Date clientExpires
	AmazonEC2Client client


	AmazonEC2Client getClient() {
		if(client) {
			if(clientExpires == null || clientExpires > new Date()) {
				return client
			}
		}

		AWSCredentials credentials = null
		if (accessKey && secretKey && token) {
			credentials = new BasicSessionCredentials (accessKey, secretKey, token)
		}
		else if (accessKey && secretKey && !token) {
			credentials = new BasicAWSCredentials(accessKey, secretKey)
		}

		def credentialsProvider

		if (credentials) {
			credentialsProvider = new AWSStaticCredentialsProvider(credentials)
		} else {
			if(useHostCredentials) {
				credentialsProvider = new InstanceProfileCredentialsProvider()

			} else {
				credentialsProvider = new DefaultAWSCredentialsProviderChain()
			}

		}
		if(stsAssumeRole) {

			AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder.standard().withCredentials(credentialsProvider).build()
			AssumeRoleResult roleResult = sts.assumeRole(new AssumeRoleRequest().withRoleArn(stsAssumeRole).withRoleSessionName('karman').withExternalId(stsExternalId))
			def roleCredentials = roleResult.credentials

			credentials = new BasicSessionCredentials(roleCredentials.getAccessKeyId(), roleCredentials.getSecretAccessKey(), roleCredentials.getSessionToken());
			if(roleCredentials) {
				credentialsProvider = new AWSStaticCredentialsProvider(credentials)
			}
			clientExpires = roleCredentials.getExpiration()
		}

		ClientConfiguration clientConfiguration = new ClientConfiguration()

		if(proxyHost)
			clientConfiguration.setProxyHost(proxyHost)
		if(proxyPort)
			clientConfiguration.setProxyPort(proxyPort)
		if(proxyUser)
			clientConfiguration.setProxyUsername(proxyUser)
		if(proxyPassword)
			clientConfiguration.setProxyPassword(proxyPassword)
		if(proxyDomain)
			clientConfiguration.setProxyDomain(proxyDomain)
		if(proxyWorkstation)
			clientConfiguration.setProxyWorkstation(proxyWorkstation)
		if(noProxy) {
            clientConfiguration.setNonProxyHosts(noProxy)
		}
		client = new AmazonEC2Client(credentialsProvider, clientConfiguration)
		if (region) {
            Region region = RegionUtils.getRegion(region)
            client.region = region
        }
		if(endpoint) //"ec2.us-west-2.amazonaws.com"
			client.setEndpoint(endpoint)
		return client

	}

	String getProviderName() {
		return this.providerName
	}

	@Override
	Collection<SecurityGroupInterface> getSecurityGroups(Map options = [:]) {

		DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest()
		if(options.vpcId) {
			request.getFilters().add(new Filter().withName("vpc-id").withValues(options.vpcId))
		}
		if(options?.name) {
			request.withGroupNames(options.name)
		}
		DescribeSecurityGroupsResult response = getClient().describeSecurityGroups(request)

		response.securityGroups?.collect {
			securityGroupFromAPI(it)
		}
	}



	SecurityGroupInterface getSecurityGroup(String uid) {
		DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest().withGroupIds(uid)
		DescribeSecurityGroupsResult results = client.describeSecurityGroups(request)
		if(results.securityGroups) {
			return securityGroupFromAPI(results.securityGroups.first())
		} else {
			return null
		}
	}

	SecurityGroupInterface createSecurityGroup(String name) {
		return new AmazonSecurityGroup(provider: this, name: name)
	}

	AmazonSecurityGroup securityGroupFromAPI(SecurityGroup rule) {
		new AmazonSecurityGroup(provider: this, name: rule.groupName, id: rule.groupId, description: rule.description, vpcId: rule.vpcId, secGroup: rule, loaded:true)
	}
}
