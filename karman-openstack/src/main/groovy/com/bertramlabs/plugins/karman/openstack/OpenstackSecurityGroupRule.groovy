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
	private String previousId

	private String ethertype
	
	public OpenstackSecurityGroupRule(OpenstackNetworkProvider provider, SecurityGroupInterface securityGroup, Map options) {
		super(securityGroup)

		this.provider = provider
		
		initializeFromOptions(options)
	}

	@Override
	public String getId() {
		return id
	}

	public String getPreviousId() {
		return previousId
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
		this.previousId = this.getId() // Store off the existing Id as it gets replaced during save :(
		def accessInfo = provider.getAccessInfo()
		def opts = [body: createPayload(), query: [tenant_id: accessInfo.projectId]]

		if(getId()) {
			// There is no update.. so delete and add
			provider.callApi(accessInfo.endpointInfo.networkApi, "/${accessInfo.endpointInfo.networkVersion}/security-group-rules/${getId()}", [:], 'DELETE')
		}
		
		def	result = provider.callApi(accessInfo.endpointInfo.networkApi, "/${accessInfo.endpointInfo.networkVersion}/security-group-rules", opts, 'POST')
		

		if(!result.success) {
			throw new RuntimeException("Error in creating or updating security group rule: ${result.error}")
		} else {
			initializeFromOptions(result.content?.security_group_rule)
		}
	}

	@Override
	public void delete() {
		if(getId()) {
			def accessInfo = provider.getAccessInfo()
			provider.callApi(accessInfo.endpointInfo.networkApi, "/${accessInfo.endpointInfo.networkVersion}/security-group-rules/${getId()}", [:], 'DELETE')
		}
		securityGroup.removeRule(this)
	}
	

	
	void setEthertype(String ethertype) {
		this.ethertype = ethertype
	}

	String getEthertype() {
		return ethertype
	}

	private initializeFromOptions(options) {
		this.options = options
		this.id = options?.id
		if(options?.from_port) {
			this.setMinPort(options?.from_port)
		} else if(options?.port_range_min) {
			this.setMinPort(options?.port_range_min)
		}
		if(options?.to_port) {
			this.setMaxPort(options?.to_port)
		} else if(options?.port_range_max) {
			this.setMaxPort(options?.port_range_max)
		}
		if(options?.ip_protocol) {
			this.setIpProtocol(options?.ip_protocol)
		} else if(options?.protocol) {
			this.setIpProtocol(options?.protocol)
		}
		if(options?.ip_range?.cidr) {
			this.addIpRange(options.ip_range.cidr)
		} else if(options?.remote_ip_prefix) {
			this.addIpRange(options?.remote_ip_prefix)
		}
		if(options?.direction) {
			this.setDirection(options.direction)
		}
		if(options?.ethertype) {
			this.setEthertype(options.ethertype)
		}
		if(options?.remote_group_id) {
			this.setTargetGroupId(options.remote_group_id)	
		}
	}

	private createPayload() {
		def payload = [
			tenant_id: provider.getAccessInfo().projectId,
			security_group_rule: [
				port_range_min: this.getMinPort(),
				port_range_max: this.getMaxPort(),
				protocol: this.getIpProtocol(),
				remote_ip_prefix: (this.getIpRange().size() > 0 ? this.getIpRange().first() : null),
				direction: this.getDirection()
			]
		]
		
		if(this.getEthertype()) {
			// only include in payload if a value is set, null is not allowed.
			payload.security_group_rule.ethertype = this.getEthertype()
		}

		if(this.getId()) {
			payload.id = this.getId()
		}

		if(this.getSecurityGroup().getId()) {
			payload.security_group_rule.security_group_id = this.getSecurityGroup().getId()
		}

		if(this.getTargetGroupId()) {
			payload.security_group_rule.remote_group_id = this.getTargetGroupId()
		}

		payload
	}

}
