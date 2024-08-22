package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Slf4j
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureFileStorageProvider
import spock.lang.Specification

@Slf4j
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


	def "can list files in a directory"() {
		given:
		def dir = storageProvider['qa-automation']
		dir.save()
		dir['test.txt'].text("Hello From Spock!").save()
		dir['hello/test sub.txt'].text("Hello From Spock!").save()
		dir['test2.txt'].text("Hello From Spock!").save()
		when:
		def files = dir.listFiles()
		def subfiles = dir.listFiles(prefix:'hello/')
		println files
		then:
		files.size() > 0
		subfiles.size() == 1
		dir['hello/test sub.txt'].getText('UTF-8') == "Hello From Spock!"
		cleanup:
		dir['test.txt'].delete()
		dir['hello/test sub.txt'].delete()
		dir['test2.txt'].delete()
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