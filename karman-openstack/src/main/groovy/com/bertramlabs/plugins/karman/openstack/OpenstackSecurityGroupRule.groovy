package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.network.SecurityGroup
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import com.bertramlabs.plugins.karman.network.SecurityGroupRule
import com.bertramlabs.plugins.karman.network.SecurityGroupRuleInterface
import groovy.util.logging.Commons

/**
 *
 * @author Bob Whiton
 */
@Commons
public class OpenstackSecurityGroupRule extends SecurityGroupRule {

	private OpenstackNetworkProvider provider
	private Map options
	private String id
	
	public OpenstackSecurityGroupRule(OpenstackNetworkProvider provider, SecurityGroupInterface securityGroup, Map options) {
		super(securityGroup)

		this.provider = provider
		
		initializeFromOptions(options)
	}

	@Override
	public String getId() {
		return id
	}

	@Override
	public void addIpRange(String ipRange) {
		// OpenStack only supports a single cidr
		this.ipRange.clear()
		this.ipRange << ipRange
	}

	@Override
	public void addIpRange(List<String> ipRange) {
		// OpenStack only supports a single cidr
		this.ipRange.clear()
		if(ipRange.size()) {
			this.ipRange << ipRange.first()
		}
	}

	@Override
	public void removeIpRange(String ipRange) {
		this.ipRange.remove(ipRange)
	}

	@Override
	public void removeIpRange(List<String> ipRange) {
		ipRange.each { it ->
			this.ipRange.remove(it)
		}
	}

	@Override
	public void save() {
		def accessInfo = provider.getAccessInfo()
		def opts = [body: createPayload()]

		if(getId()) {
			// There is no update.. so delete and add
			provider.callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-group-rules/${getId()}", [:], 'DELETE')
		}
		
		def	result = provider.callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-group-rules", opts, 'POST')
		

		if(!result.success) {
			throw new RuntimeException("Error in creating or updating security group rule: ${result.error}")
		} else {
			initializeFromOptions(result.content)
		}
	}

	@Override
	public void delete() {
		if(getId()) {
			def accessInfo = provider.getAccessInfo()
			provider.callApi(accessInfo.endpointInfo.computeApi, "/${accessInfo.endpointInfo.computeVersion}/${accessInfo.projectId}/os-security-group-rules/${getId()}", [:], 'DELETE')
		}
		securityGroup.removeRule(this)
	}

	private initializeFromOptions(options) {
		this.options = options
		this.id = options?.id
		if(options?.from_port) {
			this.setMinPort(options?.from_port)
		}
		if(options?.to_port) {
			this.setMaxPort(options?.to_port)
		}
		if(options?.ip_protocol) {
			this.setIpProtocol(options?.ip_protocol)
		}
		if(options?.ip_range?.cidr) {
			this.addIpRange(options.ip_range.cidr)
		}
	}

	private createPayload() {
		def payload = [
			tenant_id: provider.getAccessInfo().projectId,
			security_group_rule: [
				from_port: this.getMinPort(),
				to_port: this.getMaxPort(),
				ip_protocol: this.getIpProtocol(),
				cidr: this.getIpRange()?.first()	
			]
		]

		if(this.getId()) {
			payload.id = this.getId()
		}

		if(this.getSecurityGroup().getId()) {
			payload.security_group_rule.parent_group_id = this.getSecurityGroup().getId()
		}

		payload
	}

}