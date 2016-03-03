package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.network.SecurityGroup
import groovy.util.logging.Commons

/**
 *
 * @author David Estes
 */
@Commons
public class OpenstackSecurityGroup extends SecurityGroup {
	private OpenstackNetworkProvider provider
	private Map options

	public OpenstackSecurityGroup(OpenstackNetworkProvider provider,Map options) {
		this.provider = provider
		this.options = options

	}
}