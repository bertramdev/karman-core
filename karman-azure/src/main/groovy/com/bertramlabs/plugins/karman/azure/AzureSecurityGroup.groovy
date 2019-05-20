package com.bertramlabs.plugins.karman.azure

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
class AzureSecurityGroup extends SecurityGroup{
	String id
	String name
	String location
	String etag
	Object tags
	Object properties = [:]
	String resourceGroup

	AzureNetworkProvider provider

	private List<AzureSecurityGroupRule> rulesList = new ArrayList<AzureSecurityGroupRule>()
	private List<AzureSecurityGroupRule> rulesToRemove = new ArrayList<AzureSecurityGroupRule>()

	public AzureSecurityGroup(AzureNetworkProvider provider, opts = [:]) {
		this.provider = provider
		id = opts.id
		name = opts.name
		location = opts.location
		tags = opts.tags
		etag = opts.etag
		properties = opts.properties
		resourceGroup = opts.resourceGroup
		loadRules()
	}

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
		if(this.id) {
			throw new Exception('Unable to change the name on a created security group')
		}
		this.name = name
	}

	@Override
	String getDescription() {
		return this.name
	}

	String getLocation() {
		return this.location
	}

	String getEtag() {
		return this.etag
	}

	Object getTags() {
		return this.tags
	}

	@Override
	String getVpcId() {
		// Not implemented
	}

	@Override
	void setVpcId(String vpcId) {
		// Not implemented
	}

	@Override
	void setDescription(String description) {
		// Not implemented in Azure
	}

	public String getResourceGroup(){
		this.resourceGroup
	}

	public setResourceGroup(String resourceGroup) {
		this.resourceGroup = resourceGroup
	}

	@Override
	Collection<SecurityGroupRuleInterface> getRules() {
		return rulesList
	}

	private loadRules() {
		properties?.securityRules?.each { rule ->
			rulesList << new AzureSecurityGroupRule(provider, this, [id: rule.id, name: rule.name, properties: rule.properties])
		}
	}

	@Override
	SecurityGroupRuleInterface createRule() {
		def rule = new AzureSecurityGroupRule(provider, this, [direction:'ingress'])
		rulesList << rule
		return rule
	}

	@Override
	void removeRule(SecurityGroupRuleInterface rule) {
		if(rule) {
			rulesToRemove.add(rule)
			rulesToRemove.unique()
			rulesList.remove(rule)
		}
	}

	@Override
	void clearRules() {
		rulesToRemove += rules
		rulesToRemove.unique()
		rulesList.clear()
	}

	@Override
	void save() {
		// Must always start with the payload from Azure and then overlay our changes on top
		def apiPath = "/subscriptions/${provider.subscriptionId}/resourceGroups/${resourceGroup}/providers/Microsoft.Network/networkSecurityGroups/${name}"

		def body = [:]
		if (this.id) {
			def results = provider.callApi(apiPath, [query: ['api-version': '2018-11-01']], 'GET')
			def parsedResult = new groovy.json.JsonSlurper().parseText(results.content)
			if (parsedResult.error) {
				throw new RuntimeException("Error getting existing Security Group: ${parsedResult.error?.message}")
			}
			body = parsedResult
		}
		body.properties = body.properties ?: [:]
		body.location = location

		def results = provider.callApi(apiPath, [query: ['api-version': '2018-11-01'], body: body], 'PUT')
		def parsedResult = new groovy.json.JsonSlurper().parseText(results.content)
		if (parsedResult.error) {
			throw new RuntimeException("Error saving Security Group: ${parsedResult.error?.message}")
		}
		this.id = parsedResult.id

		rulesToRemove?.each { SecurityGroupRuleInterface rule ->
			rule?.delete()
		}
		rulesToRemove.clear()
		rulesList?.each { SecurityGroupRuleInterface rule ->
			rule?.save()
		}

		// Reload the security group
		results = provider.callApi(apiPath, [query: ['api-version': '2018-11-01']], 'GET')
		parsedResult = new groovy.json.JsonSlurper().parseText(results.content)
		if (parsedResult.error) {
			throw new RuntimeException("Error getting existing Security Group: ${parsedResult.error?.message}")
		}
		properties = parsedResult.properties
		location = parsedResult.location
		etag = parsedResult.etag
		tags = parsedResult.tags
		loadRules()
	}

	@Override
	void delete() {
		if(id) {
			def apiPath = "/subscriptions/${provider.subscriptionId}/resourceGroups/${resourceGroup}/providers/Microsoft.Network/networkSecurityGroups/${name}"
			def results = provider.callApi(apiPath, [query: ['api-version': '2018-11-01']], 'DELETE')
			def parsedResult = new groovy.json.JsonSlurper().parseText(results.content ?: '{}')
			if (parsedResult.error) {
				throw new RuntimeException("Error deleting Security Group: ${parsedResult.error?.message}")
			}
			id = null
		}
	}

}
