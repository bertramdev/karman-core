package com.bertramlabs.plugins.karman.aws

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressResult
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressResult
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.ec2.model.RevokeSecurityGroupEgressRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupEgressResult
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressResult
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import com.bertramlabs.plugins.karman.network.SecurityGroupRule
import groovy.util.logging.Commons

@Commons
class AmazonSecurityGroupRule extends SecurityGroupRule {
	private AmazonNetworkProvider provider
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
	private String description
	private IpPermission ipPermission

	private boolean modified = false
	private boolean existing = false

	AmazonSecurityGroupRule(AmazonNetworkProvider provider, SecurityGroupInterface securityGroup, Map options) {
		super(securityGroup)

		this.provider = provider


		initializeFromOptions(options)
	}

	private void initializeFromOptions(Map options) {
		ipPermission = options?.ipPermission
		ethertype = options?.ethertype ?: 'internet'
		direction = options?.direction
		description = options?.description
		minPort = options?.minPort
		maxPort = options?.maxPort
		targetGroupName = options?.targetGroupName
		targetGroupId = options?.targetGroupId
		ipProtocol = options?.ipProtocol
		policy = options?.policy
		targetGroupOwnerId = options?.targetGroupOwnerId
		existing = options?.existing

		originalMinPort = minPort
		originalMaxPort = maxPort
		originalEtherType = ethertype
		originalIpProtocol = ipProtocol
		originalDirection = direction
		originalPolicy = policy
		originalGroupId = targetGroupId
		originalGroupOwnerId = targetGroupOwnerId
		if(existing) {
			modified = false
		}
	}


	@Override
	public void addIpRange(String ipRange) {
		this.ipRange << ipRange
		modified = true
	}

	@Override
	public void addIpRange(List<String> ipRange) {
		// OpenStack only supports a single cidr
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
		return ethertype
	}

	@Override
	public void setMinPort(Integer port) {
		super.setMinPort(port)
		modified = true
	}



	@Override
	public void setMaxPort(Integer port) {
		super.setMaxPort(port)
		modified = true
	}



	@Override
	public void setIpProtocol(String protocol) {
		super.setIpProtocol(protocol)
		modified = true
	}

	@Override
	void setPolicy(String policy) {
		super.setPolicy(policy)
		modified = true
	}

	@Override
	void setTargetGroupName(String groupName) {
		super.setTargetGroupName(targetGroupName)
		modified = true
	}
	@Override
	void setTargetGroupId(String groupId) {
		super.setTargetGroupId(groupId)
		modified = true
	}


	@Override
	void setDirection(String targetDirection) {
		super.setDirection(targetDirection)
		modified = true
	}

	@Override
	String getDirection() {
		return this.direction
	}

	@Override
	void setDescription(String targetDescription) {
		super.setDescription(targetDescription)
		modified = true
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

		if(this.ipRange?.size()) {
			originalCidr = ipRange[0]
		}

		ipPermission = new IpPermission()
		ipPermission.withIpProtocol(ipProtocol)
		ipPermission.withFromPort(minPort)
		ipPermission.withToPort(maxPort)

		if(!this.ipRange?.size()) {
			throw new Exception('Must specify an ipRange / cidr')
		}

		ipPermission.withIpv4Ranges(new IpRange().withCidrIp(ipRange[0]).withDescription(this.description))

		if(direction == 'egress') {
			AuthorizeSecurityGroupEgressRequest request = new AuthorizeSecurityGroupEgressRequest()
			request.withIpPermissions(ipPermission)
			request.withGroupId(this.securityGroup.id)

			AuthorizeSecurityGroupEgressResult response = client.authorizeSecurityGroupEgress(request)
		} else {
			AuthorizeSecurityGroupIngressRequest request = new AuthorizeSecurityGroupIngressRequest()
			request.withIpPermissions(ipPermission)
			request.withGroupId(this.securityGroup.id)
			AuthorizeSecurityGroupIngressResult response = client.authorizeSecurityGroupIngress(request)
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
		def cidr = ipRange[0]
		ipPermission = new IpPermission()
		ipPermission.withIpProtocol(ipProtocol)
		ipPermission.withFromPort(minPort)
		ipPermission.withToPort(maxPort)
		ipPermission.withIpv4Ranges(new IpRange().withCidrIp(ipRange[0]))

		if(originalDirection == 'egress') {
			RevokeSecurityGroupEgressRequest egressRequest = new RevokeSecurityGroupEgressRequest()
			egressRequest.withIpPermissions(ipPermission)
			egressRequest.setGroupId(this.securityGroup.id)
			RevokeSecurityGroupEgressResult response = client.revokeSecurityGroupEgress(egressRequest)
		} else {
			RevokeSecurityGroupIngressRequest ingressRequest = new RevokeSecurityGroupIngressRequest()
			ingressRequest.withIpPermissions(ipPermission)
			ingressRequest.setGroupId(this.securityGroup.id)
			RevokeSecurityGroupIngressResult response = client.revokeSecurityGroupIngress(ingressRequest)
		}
	}

	private AmazonEC2Client getClient() {
		return ((AmazonNetworkProvider)provider).getClient()
	}
}
