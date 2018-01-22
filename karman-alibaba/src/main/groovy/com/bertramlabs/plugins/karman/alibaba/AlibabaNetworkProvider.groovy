package com.bertramlabs.plugins.karman.alibaba

import com.aliyuncs.DefaultAcsClient
import com.aliyuncs.IAcsClient
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeRequest
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeResponse
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsRequest
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse
import com.aliyuncs.profile.DefaultProfile
import com.bertramlabs.plugins.karman.network.NetworkProvider
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import groovy.util.logging.Commons

@Commons
class AlibabaNetworkProvider extends NetworkProvider {
	static String providerName = "alibaba"

	String accessKey = ''
	String secretKey = ''
	String region = 'cn-hangzhou'
	private IAcsClient client

	IAcsClient getClient() {
		if(client) {
			return client
		} else {
			DefaultProfile profile = DefaultProfile.getProfile(region, accessKey, secretKey)
			client = new DefaultAcsClient(profile)
			return client
		}
	}

	String getProviderName() {
		return this.providerName
	}

	@Override
	Collection<SecurityGroupInterface> getSecurityGroups(Map options = [:]) {
		DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest()
		if(options?.name) {
			request.setSecurityGroupName(options.name)
		}
		DescribeSecurityGroupsResponse response = getClient().getAcsResponse(request)
		response.securityGroups?.collect {
			securityGroupFromAPI(it)
		}

	}


	SecurityGroupInterface getSecurityGroup(String uid) {
		DescribeSecurityGroupAttributeRequest request = new DescribeSecurityGroupAttributeRequest()
		request.setSecurityGroupId(uid)
		DescribeSecurityGroupAttributeResponse response = getClient().getAcsResponse(request)
		return new AlibabaSecurityGroup(provider: this, name: response.getSecurityGroupName(), id: response.getSecurityGroupId(), description: response.getDescription(), vpcId: response.getSecurityGroupId(), loaded:true)

	}

	SecurityGroupInterface createSecurityGroup(String name) {
		return new AlibabaSecurityGroup(provider: this, name: name)
	}

	AlibabaSecurityGroup securityGroupFromAPI(DescribeSecurityGroupsResponse.SecurityGroup rule) {
		new AlibabaSecurityGroup(provider: this, name: rule.getSecurityGroupName(), id: rule.getSecurityGroupId(), description: rule.getDescription(), vpcId: rule.getSecurityGroupId(), loaded:true)
	}
}
