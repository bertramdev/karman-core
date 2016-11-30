package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Commons
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureStorageProvider
import spock.lang.Specification

@Commons
class AzureStorageProviderSpec extends Specification {

	static AzureStorageProvider storageProvider

	def setupSpec() {
		storageProvider = AzureStorageProvider.create(
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
}