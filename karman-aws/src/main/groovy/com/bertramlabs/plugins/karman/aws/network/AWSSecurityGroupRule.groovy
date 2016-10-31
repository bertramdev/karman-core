package com.bertramlabs.plugins.karman.aws.network

import com.amazonaws.services.ec2.model.IpPermission
import com.bertramlabs.plugins.karman.network.SecurityGroupRule

/**
 * AWS Representation within Karman of a {@link SecurityGroupRule}. Provides information for ingress rules
 * (and potentially Egress rules in the future) for security groups. This includes port ranges, ip ranges (CIDRs), and
 * ip protocols (i.e. tcp/udp)
 * @author David Estes
 */
class AWSSecurityGroupRule extends SecurityGroupRule {
	String id
	public AWSSecurityGroupRule(AWSNetworkProvider provider, AWSSecurityGroup securityGroup, IpPermission ipPermission, Integer id) {
		super(securityGroup)
		this.id = securityGroup.getId() + "-${id}"
		this.ipProtocol = ipPermission.getIpProtocol()
		this.ipRange = ipPermission.getIpRanges()
		this.maxPort = ipPermission.getToPort()
		this.minPort = ipPermission.getFromPort()
	}


}
