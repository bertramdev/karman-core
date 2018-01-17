package com.bertramlabs.plugins.karman.alibaba

import com.aliyuncs.IAcsClient
import com.aliyuncs.ecs.model.v20140526.AuthorizeSecurityGroupEgressRequest
import com.aliyuncs.ecs.model.v20140526.AuthorizeSecurityGroupEgressResponse
import com.aliyuncs.ecs.model.v20140526.AuthorizeSecurityGroupRequest
import com.aliyuncs.ecs.model.v20140526.AuthorizeSecurityGroupResponse
import com.aliyuncs.ecs.model.v20140526.RevokeSecurityGroupEgressRequest
import com.aliyuncs.ecs.model.v20140526.RevokeSecurityGroupEgressResponse
import com.aliyuncs.ecs.model.v20140526.RevokeSecurityGroupRequest
import com.aliyuncs.ecs.model.v20140526.RevokeSecurityGroupResponse
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import com.bertramlabs.plugins.karman.network.SecurityGroupRule

class AlibabaSecurityGroupRule extends SecurityGroupRule{

	private AlibabaNetworkProvider provider
	private String direction
	private String ethertype = 'internet'

	private String originalGroupId
	private String originalGroupOwnerId
	private String originalEtherType
	private String originalDirection
	private String originalPolicy
	private Integer originalMinPort
	private Integer originalMaxPort
	private String targetGroupOwnerId
	private String originalIpProtocol
	private String originalCidr

	private boolean modified = false
	private boolean existing = false

	AlibabaSecurityGroupRule(AlibabaNetworkProvider provider, SecurityGroupInterface securityGroup, Map options) {
		super(securityGroup)

		this.provider = provider


		initializeFromOptions(options)
	}

	private void initializeFromOptions(Map options) {
		this.ethertype = options?.ethertype ?: 'internet'
		this.direction = options?.direction
		this.minPort = options?.minPort
		this.maxPort = options?.maxPort
		this.targetGroupName = options?.targetGroupName
		this.targetGroupId = options?.targetGroupId
		this.ipProtocol = options?.ipProtocol
		this.policy = options?.policy
		this.targetGroupOwnerId = options?.targetGroupOwnerId
		this.existing = options?.existing
		if(options?.cidr) {
			this.ipRange << options.cidr
			originalCidr = options.cidr

		}

		originalMinPort = minPort
		originalMaxPort = maxPort
		originalEtherType = ethertype
		originalIpProtocol = ipProtocol
		originalDirection = direction
		originalPolicy = policy
		originalGroupId = targetGroupId
		originalGroupOwnerId = targetGroupOwnerId
	}


	@Override
	public void addIpRange(String ipRange) {
		// OpenStack only supports a single cidr
		this.ipRange.clear()
		this.ipRange << ipRange
		modified = true
	}

	@Override
	public void addIpRange(List<String> ipRange) {
		// OpenStack only supports a single cidr
		this.ipRange.clear()
		if(ipRange.size()) {
			this.ipRange << ipRange.first()
		}
		modified = true
	}

	@Override
	public void removeIpRange(String ipRange) {
		this.ipRange.remove(ipRange)
		modified = true
	}

	@Override
	public void removeIpRange(List<String> ipRange) {
		ipRange.each { it ->
			this.ipRange.remove(it)
		}
		modified = true
	}


	void setEthertype(String ethertype) {
		this.ethertype = ethertype //valid options are internet intranet
		modified = true
	}

	String getEthertype() {
		return this.ethertype
	}

	@Override
	public void setMinPort(Integer port) {
		this.minPort = port
		modified = true
	}

	@Override
	public Integer getMinPort() {
		return minPort
	}

	@Override
	public void setMaxPort(Integer port) {
		this.maxPort = port
		modified = true
	}

	@Override
	public Integer getMaxPort() {
		return maxPort
	}

	@Override
	public void setIpProtocol(String protocol) {
		this.ipProtocol = protocol?.toLowerCase()
		modified = true
	}

	@Override
	public String getIpProtocol() {
		return this.ipProtocol
	}

	@Override
	String getPolicy() {
		return this.policy
	}

	@Override
	void setPolicy(String policy) {
		this.policy = policy
		modified = true
	}

	@Override
	void setTargetGroupName(String targetGroupName) {
		this.targetGroupName = targetGroupName
		modified = true
	}
	@Override
	void setTargetGroupId(String targetGroupId) {
		this.targetGroupId = targetGroupId
		modified = true
	}
	@Override
	String getTargetGroupName() {
		return this.targetGroupName
	}
	@Override
	String getTargetGroupId() {
		return this.targetGroupId
	}

	@Override
	void setDirection(String direction) {
		this.direction = direction
		modified = true
	}

	@Override
	String getDirection() {
		return direction
	}

	@Override
	String getId() {
		return null
	}


	@Override
	void save() {
		if(modified && existing) {
			delete()
		}

		if(direction == 'egress') {
			AuthorizeSecurityGroupEgressRequest request = new AuthorizeSecurityGroupEgressRequest()
			request.setPortRange("${minPort}/${maxPort}")
			if(this.ipRange?.size()) {
				request.setDestCidrIp(this.ipRange[0])
			}

			request.setDestGroupId(this.targetGroupId)
			request.setDestGroupOwnerId(this.targetGroupOwnerId)
			request.setNicType(this.ethertype)
			request.setPolicy(this.policy)
			request.setIpProtocol(this.ipProtocol)
			request.setSecurityGroupId(this.securityGroup.id)
			AuthorizeSecurityGroupEgressResponse response = client.getAcsResponse(request)
		} else {
			AuthorizeSecurityGroupRequest request = new AuthorizeSecurityGroupRequest()
			request.setPortRange("${minPort}/${maxPort}")
			if(this.ipRange?.size()) {
				request.setSourceCidrIp(this.ipRange[0])
			}

			request.setSourceGroupId(this.targetGroupId)
			request.setSourceGroupOwnerId(this.targetGroupOwnerId)
			request.setNicType(this.ethertype)
			request.setPolicy(this.policy)
			request.setIpProtocol(this.ipProtocol)
			request.setSecurityGroupId(this.securityGroup.id)
			AuthorizeSecurityGroupResponse response = client.getAcsResponse(request)
		}


		if(this.ipRange?.size()) {
			originalCidr = ipRange[0]
		}

		originalMinPort = minPort
		originalMaxPort = maxPort
		originalEtherType = ethertype
		originalIpProtocol = ipProtocol
		originalDirection = direction
		originalPolicy = policy
		originalGroupId = targetGroupId
		originalGroupOwnerId = targetGroupOwnerId

		existing = true
		modified = false
	}

	@Override
	void delete() {
		if(originalDirection == 'egress') {
			RevokeSecurityGroupEgressRequest egressRequest = new RevokeSecurityGroupEgressRequest()
			egressRequest.setPortRange("${originalMinPort}/${originalMaxPort}")
			egressRequest.setDestCidrIp(this.originalCidr)
			egressRequest.setDestGroupId(this.originalGroupId)
			egressRequest.setDestGroupOwnerId(this.originalGroupOwnerId)
			egressRequest.setNicType(this.originalEtherType)
			egressRequest.setPolicy(this.originalPolicy)
			egressRequest.setIpProtocol(this.originalIpProtocol)
			egressRequest.setSecurityGroupId(this.securityGroup.id)
			RevokeSecurityGroupEgressResponse response = client.getAcsResponse(egressRequest)
		} else {
			RevokeSecurityGroupRequest ingressRequest = new RevokeSecurityGroupRequest()
			ingressRequest.setPortRange("${originalMinPort}/${originalMaxPort}")
			ingressRequest.setSourceCidrIp(this.originalCidr)
			ingressRequest.setSourceGroupId(this.originalGroupId)
			ingressRequest.setSourceGroupOwnerId(this.originalGroupOwnerId)
			ingressRequest.setNicType(this.originalEtherType)
			ingressRequest.setPolicy(this.originalPolicy)
			ingressRequest.setIpProtocol(this.originalIpProtocol)
			ingressRequest.setSecurityGroupId(this.securityGroup.id)
			RevokeSecurityGroupResponse response = client.getAcsResponse(ingressRequest)
		}
	}

	private IAcsClient getClient() {
		return ((AlibabaNetworkProvider)provider).getClient()
	}
}
