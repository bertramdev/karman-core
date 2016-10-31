package com.bertramlabs.plugins.karman.aws.network

import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.IpPermission
import com.bertramlabs.plugins.karman.network.SecurityGroup
import com.bertramlabs.plugins.karman.network.SecurityGroupRuleInterface

/**
 * Created by davydotcom on 10/29/16.
 */
class AWSSecurityGroup extends SecurityGroup{
	private AWSNetworkProvider provider
	private String id
	private Map options

	com.amazonaws.services.ec2.model.SecurityGroup awsSecurityGroup
	private List<AWSSecurityGroupRule> rules = new ArrayList<AWSSecurityGroupRule>()

	public AWSSecurityGroup(AWSNetworkProvider provider, Map options) {
		this.provider = provider
		initializeFromOptions(options)
	}

	public AWSSecurityGroup(AWSNetworkProvider provider, com.amazonaws.services.ec2.model.SecurityGroup awsSecurityGroup) {
		this.awsSecurityGroup = awsSecurityGroup
		this.provider = provider
		this.name = awsSecurityGroup.groupName
		this.id = awsSecurityGroup.groupId
		this.description = awsSecurityGroup.description
	}

	private initializeFromOptions(options) {
		this.options = options
		rules = new ArrayList<AWSSecurityGroupRule>()

		def securityGroup = this
		options?.rules?.each { ruleMeta ->
			rules << new AWSSecurityGroupRule(provider, securityGroup, ruleMeta)
		}
	}

	@Override
	String getId() {
		return this.id
	}

	@Override
	String getName() {
		return this.name
	}

	@Override
	void setName(String name) {
		this.name = name
	}

	@Override
	String getDescription() {
		return this.description
	}

	@Override
	void setDescription(String description) {

	}

	@Override
	Collection<SecurityGroupRuleInterface> getRules() {
		if(!rules && awsSecurityGroup?.getIpPermissions().size() > 0) {
			def securityGroup = this
			int position = 0
			awsSecurityGroup.ipPermissions.collect { IpPermission ipPermission ->
				new AWSSecurityGroupRule(provider, securityGroup, ipPermission, position++)
			}
		}
		return null
	}

	@Override
	SecurityGroupRuleInterface createRule() {
		return null
	}

	@Override
	void removeRule(SecurityGroupRuleInterface rule) {

	}

	@Override
	void clearRules() {

	}

	@Override
	void save() {
		if(!this.id) {
			CreateSecurityGroupRequest request = new CreateSecurityGroupRequest()
			if(provider.vpc) {
				request.setVpcId(provider.vpc)
			}
			request.setGroupName(this.getName())
			request.setDescription(this.getDescription())
			this.id = provider.getEC2Client().createSecurityGroup(request).getGroupId()
			DescribeSecurityGroupsRequest securityRequest = new DescribeSecurityGroupsRequest().withFilters(new LinkedList<Filter>())
			if(provider.vpc) {
				securityRequest.getFilters().add(new Filter().withName("vpc-id").withValues(provider.vpc))
			}
			securityRequest.getGroupIds().add(this.id)

			awsSecurityGroup = provider.getEC2Client().describeSecurityGroups(securityRequest).getSecurityGroups()[0]
		}


//		We need to diff ip permissions and do some work




	}

	@Override
	void delete() {
		DeleteSecurityGroupRequest delRequest = new DeleteSecurityGroupRequest()
		delRequest.setGroupId(this.id)
		provider.getEC2Client().deleteSecurityGroup(delRequest)
	}
}
