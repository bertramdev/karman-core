package com.bertramlabs.plugins.karman.cifs

import com.bertramlabs.plugins.karman.CloudFile
import groovy.util.logging.Commons
import spock.lang.Specification

@Commons
class CifsStorageProviderSpec extends Specification {

	static CifsStorageProvider storageProvider

	def setupSpec() {
		storageProvider = CifsStorageProvider.create(
			provider:'cifs',
			host: System.getProperty('cifs.host'),
			domain: '.',
			username: System.getProperty('cifs.username'),
			password: System.getProperty('cifs.password')
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
		log.info("Shares? ${shares?.collect{it.name}}")
		then:
		shares != null
	}

	def "getProviderName"() {
		expect:
		storageProvider.getProviderName() == 'cifs'
	}

	def "can write to a file"() {
		given:

		def dir = storageProvider[System.getProperty('cifs.share')]
		when:
			CloudFile file = dir['test.txt'].text("Hello From Spock!")
			file.save()
		then:
			file.getText("UTF-8") == "Hello From Spock!"
		cleanup:
			file?.delete()
	}

	def "can list files in a directory"() {
		given:
			def dir = storageProvider[System.getProperty('cifs.share')]
		when:
			def files = dir.listFiles()
			println files
		then:
			files.size() > 0
	}

	// def "storage provider with http"() {
	// 	setup:
	// 	storageProvider = AzureFileStorageProvider.create(
	// 		provider:'azure',
	// 		storageAccount:System.getProperty('azure.storageAccount'),
	// 		storageKey:System.getProperty('azure.storageKey'),
	// 		protocol: 'http'
	// 	)

	// 	when:
	// 	def shares = storageProvider.getDirectories()

	// 	then:
	// 	shares != null
	// 	storageProvider.getEndpointUrl().startsWith('http')
	// 	!storageProvider.getEndpointUrl().startsWith('https')
	// }
}