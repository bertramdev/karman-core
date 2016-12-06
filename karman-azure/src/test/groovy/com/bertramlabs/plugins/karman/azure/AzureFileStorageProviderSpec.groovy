package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Commons
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureFileStorageProvider
import spock.lang.Specification

@Commons
class AzureFileStorageProviderSpec extends Specification {

	static AzureFileStorageProvider storageProvider

	def setupSpec() {
		storageProvider = AzureFileStorageProvider.create(
			provider:'azure',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey')
		)
	}

	def "storage provider creates"() {
		expect:
		storageProvider != null
	}

	def "getDirectories"() {
		when:
		def shares = storageProvider.getDirectories()
		log.info "size of shares: ${shares.size()}"
		
		then:
		shares != null
	}

	def "getProviderName"() {
		expect:
		storageProvider.getProviderName() == 'azure'
	}

	def "storage provider with http"() {
		setup:
		storageProvider = AzureFileStorageProvider.create(
			provider:'azure',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey'),
			protocol: 'http'
		)

		when:
		def shares = storageProvider.getDirectories()

		then:
		shares != null
		storageProvider.getEndpointUrl().startsWith('http')
		!storageProvider.getEndpointUrl().startsWith('https')
	}
}