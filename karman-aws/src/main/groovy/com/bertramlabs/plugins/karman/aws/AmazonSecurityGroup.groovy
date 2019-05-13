package com.bertramlabs.plugins.karman.aws

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DeleteSecurityGroupResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.IpPermission
import com.bertramlabs.plugins.karman.network.NetworkProvider
import com.bertramlabs.plugins.karman.network.SecurityGroup
import com.bertramlabs.plugins.karman.network.SecurityGroupRuleInterface
import groovy.util.logging.Commons

@Commons
class AmazonSecurityGroup extends SecurityGroup{
	String id
	String name
	String description
	String vpcId
	Boolean loaded = false
	private Boolean metadataLoaded = false
	AmazonNetworkProvider provider
	com.amazonaws.services.ec2.model.SecurityGroup secGroup
	private List<AmazonSecurityGroupRule> rulesList = new ArrayList<AmazonSecurityGroupRule>()
	private List<AmazonSecurityGroupRule> rulesToRemove = new ArrayList<AmazonSecurityGroupRule>()

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
		if(!secGroup) {
			DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest().withGroupIds(id)
			DescribeSecurityGroupsResult result = client.describeSecurityGroups(request)
			secGroup = result.securityGroups.first()
		}


		if(secGroup) {
			vpcId = secGroup.vpcId
			name = secGroup.groupName
			description = secGroup.description

			secGroup.ipPermissions?.each { IpPermission permission ->
				Integer minPort = permission.getFromPort()
				Integer maxPort = permission.getToPort()


				def options = [direction: 'ingress', ipProtocol: permission.getIpProtocol(), minPort: minPort, maxPort: maxPort, existing: true]
				def ranges = permission.getIpv4Ranges()
				if(ranges) {
					ranges.each { range ->
						AmazonSecurityGroupRule rule = new AmazonSecurityGroupRule(provider, this, options)
						rule.addIpRange(range.getCidrIp())
						this.rulesList.add(rule)
					}
				} else {
					AmazonSecurityGroupRule rule = new AmazonSecurityGroupRule(provider, this, options)
					this.rulesList.add(rule)
				}
			}

			secGroup.ipPermissionsEgress?.each { IpPermission permission ->
				Integer minPort = permission.getFromPort()
				Integer maxPort = permission.getToPort()


				def options = [direction: 'egress', ipProtocol: permission.getIpProtocol(), minPort: minPort, maxPort: maxPort, existing: true]
				def ranges = permission.getIpv4Ranges()
				if(ranges) {
					ranges?.each { range ->
						AmazonSecurityGroupRule rule = new AmazonSecurityGroupRule(provider,this,options)
						rule.addIpRange(range.getCidrIp())
						this.rulesList.add(rule)

					}
				} else {
					AmazonSecurityGroupRule rule = new AmazonSecurityGroupRule(provider,this,options)
					this.rulesList.add(rule)
				}

			}
			metadataLoaded = true
		}
	}

	@Override
	SecurityGroupRuleInterface createRule() {
		return new AmazonSecurityGroupRule(provider,this,[direction:'ingress'])
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
			securityGroup.withGroupName(name)
			securityGroup.withDescription(description)
			securityGroup.withVpcId(vpcId)
			CreateSecurityGroupResult response = client.createSecurityGroup(securityGroup)

			if(response.groupId) {
				id = response.groupId
				loaded = true
			}
		} else {
//			cant modify names or descriptions ... interesting
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
			DeleteSecurityGroupRequest request = new DeleteSecurityGroupRequest().withGroupId(id)
			DeleteSecurityGroupResult response = client.deleteSecurityGroup(request)
			id = null
			loaded = false
		}
	}

	private AmazonEC2Client getClient() {
		return provider.getClient()
	}
}
