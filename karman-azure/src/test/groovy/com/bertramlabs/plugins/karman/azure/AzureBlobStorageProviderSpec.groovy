package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Slf4j
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureBlobStorageProvider
import spock.lang.Specification

@Slf4j
class AzureBlobStorageProviderSpec extends Specification {

	static AzureBlobStorageProvider storageProvider

	def setupSpec() {
		storageProvider = AzureBlobStorageProvider.create(
			provider:'azure-pageblob',
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
		def directories = storageProvider.getDirectories()
		log.info "size of directories: ${directories.size()}"
		then:
		directories != null
	}

	def "getProviderName"() {
		expect:
		storageProvider.getProviderName() == 'azure-pageblob'
	}

	def "storage provider with http"() {
		setup:
		storageProvider = AzureBlobStorageProvider.create(
			provider:'azure-pageblob',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey'),
			protocol: 'http'
		)

		when:
		def directories = storageProvider.getDirectories()

		then:
		directories != null
		storageProvider.getEndpointUrl().startsWith('http')
		!storageProvider.getEndpointUrl().startsWith('https')
	}
}