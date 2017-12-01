package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.openstack.OpenstackNetworkProvider
import spock.lang.Specification

class OpenstackNetworkProviderSpec extends Specification {

	def "network provider creates"() {
		when:
		OpenstackNetworkProvider networkProvider = OpenstackNetworkProvider.create(
			provider:'openstack',
			username:System.getProperty('openstack.username'),
			password:System.getProperty('openstack.password'),
			apiKey:System.getProperty('openstack.apiKey'),
			identityUrl:System.getProperty('openstack.identityUrl'),
			tenantName:System.getProperty('openstack.tenantName')
		)

		then:
		networkProvider
	}
}
