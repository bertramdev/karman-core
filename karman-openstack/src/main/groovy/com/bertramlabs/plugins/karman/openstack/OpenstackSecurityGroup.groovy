package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.network.SecurityGroup
import com.bertramlabs.plugins.karman.network.SecurityGroupRuleInterface
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import groovy.util.logging.Commons
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.*
import org.apache.http.entity.*
import org.apache.http.impl.client.*
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils
import org.apache.http.client.utils.URIBuilder

/**
 *
 * @author David Estes
 */
@Commons
public class OpenstackSecurityGroup extends SecurityGroup {

	private OpenstackNetworkProvider provider
	private Map options
	private List<OpenstackSecurityGroupRule> rules = new ArrayList<OpenstackSecurityGroupRule>()

	public OpenstackSecurityGroup(OpenstackNetworkProvider provider, Map options) {
		this.provider = provider
		initializeFromOptions(options)
	}

	public String getId() {
		options.id
	}

	public String getName() {
		options.name
	}

	public void setName(String name) {
		options.name = name
	}

	public String getDescription() {
		options.description
	}

	public void setDescription(String description) {
		options.description = description
	}

	public Collection<SecurityGroupRuleInterface> getRules() {
		return rules
	}

	public SecurityGroupRuleInterface createRule() {
		def newRule = new OpenstackSecurityGroupRule(this.provider, this, [:])
		rules << newRule
		newRule
	}

	public void removeRule(SecurityGroupRuleInterface rule) {
		rules.remove(rule)
	}

	public void clearRules() {
		rules.clear()
	}

	public void save() {
		def accessInfo = provider.getAccessInfo()
		def opts = [body: createPayload()]

		def result
		def desiredRules = new ArrayList<OpenstackSecurityGroupRule>(this.getRules())
		if(getId()) {
			result = provider.callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-groups/${getId()}", opts, 'PUT')
		} else {	
			result = provider.callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-groups", opts, 'POST')
		}

		if(!result.success) {
			throw new RuntimeException("Error in creating security group: ${result.error}")
		} else {
			initializeFromOptions(result.content?.security_group)
		}

		// Delete each rule that is removed
		rules.each { currentRule ->
			// We don't want to touch rules that are based on security groups.. so ignore if we don't have an IP range
			if(currentRule.getIpRange()?.size() > 0) {
				def foundRule = desiredRules.find { it.getId() == currentRule.getId() }
				if(!foundRule) {
					def deleteResult = provider.callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-group-rules/${currentRule.getId()}", [:], 'DELETE')
					if(!result.success) {
						log.error "Error deleting rule with id of ${currentRule.getId()}"
					}					
				}
			}
		}

		// Save each rule
		clearRules()
		desiredRules.each { desiredRule -> 
			desiredRule.save()
			rules << desiredRule
		}
	}

	public void delete() {
		if(getId()) {
			def accessInfo = provider.getAccessInfo()
			def result = provider.callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-groups/${getId()}", [:], 'DELETE')
			if(!result.success) {
				throw new RuntimeException("Error in deleting security group: ${result.error}")
			}
		}
	}

	private initializeFromOptions(options) {
		this.options = options
		rules = new ArrayList<OpenstackSecurityGroupRule>()

		def securityGroup = this
		options?.rules?.each { ruleMeta -> 
			rules << new OpenstackSecurityGroupRule(provider, securityGroup, ruleMeta)
		} 
	}

	private createPayload() {
		[
			tenant_id: provider.getAccessInfo().projectId,
			security_group: [
				name: this.getName(),
				description: this.getDescription() ?: ''	
			]
		]
	}
}