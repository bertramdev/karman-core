package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Commons
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureNetworkProvider
import spock.lang.Specification

@Commons
class AzureNetworkProviderSpec extends Specification {

	static AzureNetworkProvider networkProvider

	def setupSpec() {
		def subscriptionId = System.getProperty('azureSubscriptionId')
		def tenantId = System.getProperty('azureTenantId')
		def location = System.getProperty('azureLocation')
		def clientId = System.getProperty('azureClientId')
		def clientKey = System.getProperty('azureClientKey')

		networkProvider = AzureNetworkProvider.create(
			provider:'azure',
			subscriptionId: subscriptionId,
			identityPath: "/${tenantId}/oauth2/token",
			location: location,
			tenantId: tenantId,
			clientId: clientId,
			clientKey: clientKey
		)
	}

	def "network provider creates"() {
		expect:
		networkProvider != null
	}

	def "getSecurityGroups"() {
		when:
		def securityGroups = networkProvider.getSecurityGroups()
		log.info "size of security groups: ${securityGroups.size()}"
		then:
		securityGroups != null
		securityGroups.size() > 0
	}
}