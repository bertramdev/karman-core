package com.bertramlabs.plugins.karman.alibaba

import com.aliyuncs.IAcsClient
import com.aliyuncs.ecs.model.v20140526.CreateSecurityGroupRequest
import com.aliyuncs.ecs.model.v20140526.CreateSecurityGroupResponse
import com.aliyuncs.ecs.model.v20140526.DeleteSecurityGroupRequest
import com.aliyuncs.ecs.model.v20140526.DeleteSecurityGroupResponse
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeRequest
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupAttributeResponse
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse
import com.aliyuncs.ecs.model.v20140526.ModifySecurityGroupAttributeRequest
import com.aliyuncs.ecs.model.v20140526.ModifySecurityGroupAttributeResponse
import com.bertramlabs.plugins.karman.network.NetworkProvider
import com.bertramlabs.plugins.karman.network.SecurityGroup
import com.bertramlabs.plugins.karman.network.SecurityGroupRuleInterface
import groovy.util.logging.Commons

@Commons
class AlibabaSecurityGroup extends SecurityGroup{

	String id
	String name
	String description
	String vpcId
	Boolean loaded = false
	private Boolean metadataLoaded = false
	AlibabaNetworkProvider provider
	private List<AlibabaSecurityGroupRule> rulesList = new ArrayList<AlibabaSecurityGroupRule>()
	private List<AlibabaSecurityGroupRule> rulesToRemove = new ArrayList<AlibabaSecurityGroupRule>()

	@Override
	NetworkProvider getProvider() {
		return this.provider
	}

	@Override
	String getId() {
		return id
	}

	@Override
	String getName() {
		return name
	}

	@Override
	void setName(String name) {
		this.name = name
	}

	@Override
	String getDescription() {
		return description
	}

	String getVpcId() {
		return vpcId
	}

	@Override
	void setDescription(String description) {
		this.description = description
	}

	@Override
	Collection<SecurityGroupRuleInterface> getRules() {
		if(!metadataLoaded && id) {
			loadAttributes()
		}
		return rulesList
	}




	private loadAttributes() {
		DescribeSecurityGroupAttributeRequest request = new DescribeSecurityGroupAttributeRequest()
		request.setSecurityGroupId(id)
		DescribeSecurityGroupAttributeResponse response = getClient().getAcsResponse(request)
		vpcId = response.vpcId
		description = response.description
		name = response.getSecurityGroupName()

		rulesList.clear()

		response.permissions?.each { DescribeSecurityGroupAttributeResponse.Permission permission ->
			def portArgs = permission.getPortRange()?.tokenize('/')
			Integer minPort = -1
			Integer maxPort = -1
			if(portArgs.size() > 1) {
				minPort = portArgs[0].toInteger()
				maxPort = portArgs[1].toInteger()
			}

			def options = [etherType: permission.nicType, ipProtocol: permission.getIpProtocol(), description: permission.description, minPort: minPort, maxPort: maxPort, policy: permission.policy, existing: true]

			if(permission.destCidrIp || permission.destGroupId) {
				options.cidr = permission.destCidrIp
				options.targetGroupId = permission.destGroupId
				options.targetGroupName = permission.destGroupName
				options.targetGroupOwnerId = permission.destGroupOwnerAccount
				options.direction = 'egress'
			} else {
				options.cidr = permission.sourceCidrIp
				options.targetGroupId = permission.sourceGroupId
				options.targetGroupName = permission.sourceGroupName
				options.targetGroupOwnerId = permission.sourceGroupOwnerAccount
				options.direction = 'ingress'
			}
			println "Creating Rule from options ${options}"
			AlibabaSecurityGroupRule rule = new AlibabaSecurityGroupRule(provider,this,options)
			this.rulesList.add(rule)
		}
		metadataLoaded = true


	}

	@Override
	SecurityGroupRuleInterface createRule() {
		return new AlibabaSecurityGroupRule(provider,this,[direction:'ingress'])
	}

	@Override
	void removeRule(SecurityGroupRuleInterface rule) {
		rulesToRemove.add(rule)
		rulesToRemove.unique()
		rulesList.remove(rule)
	}

	@Override
	void clearRules() {
		rulesToRemove += rules
		rulesToRemove.unique()
		rulesList.clear()
	}

	@Override
	void save() {

		if(!id) {
			CreateSecurityGroupRequest securityGroup = new CreateSecurityGroupRequest()
			securityGroup.securityGroupName = getName()
			securityGroup.setDescription(getDescription())
			securityGroup.setVpcId(getVpcId())
			com.aliyun.ecs.model.v201
			CreateSecurityGroupResponse response = client.getAcsResponse(securityGroup)
			if(response.securityGroupId) {
				id = response.securityGroupId
				loaded = true
			}
		} else {
			ModifySecurityGroupAttributeRequest securityGroupRequest = new ModifySecurityGroupAttributeRequest()
			securityGroupRequest.setDescription(description)
			securityGroupRequest.setSecurityGroupName(name)
			ModifySecurityGroupAttributeResponse response = client.getAcsResponse(securityGroupRequest)
			loaded = true
		}

		rulesToRemove?.each { SecurityGroupRuleInterface rule ->
			rule.delete()
		}
		rulesToRemove.clear()
		rulesList?.each { SecurityGroupRuleInterface rule ->
			rule.save()
		}

	}


	@Override
	void delete() {
		if(id && loaded) {
			DeleteSecurityGroupRequest request = new DeleteSecurityGroupRequest()
			request.setSecurityGroupId(id)
			DeleteSecurityGroupResponse response = client.getAcsResponse(request)
			id = null
			loaded = false
		}
	}

	private IAcsClient getClient() {
		return provider.getClient()
	}
}
