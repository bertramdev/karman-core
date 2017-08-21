package com.bertramlabs.plugins.karman.nfs

import com.bertramlabs.plugins.karman.CloudFile
import groovy.util.logging.Commons
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.nfs.NfsStorageProvider
import spock.lang.Specification

@Commons
class NfsStorageProviderSpec extends Specification {

	static NfsStorageProvider storageProvider

	def setupSpec() {
		storageProvider = NfsStorageProvider.create(
			provider:'nfs',
			host: System.getProperty('nfs.host'),
			exportFolder: System.getProperty('nfs.mount')
		)
	}

	def "storage provider creates"() {
		expect:
		storageProvider != null
	}

//	def "can verify if a mount point exists on the nfs host"() {
//		given:
//			def dir = storageProvider[System.getProperty('nfs.mount')]
//		when:
//			def result = dir.exists()
//		then:
//			result == true
//	}
//
//	def "can verify that a mount point does not exist on the nfs host"() {
//		given:
//			def dir = storageProvider['/tmp/fake']
//		when:
//			def result = dir.exists()
//		then:
//			result == false
//	}

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
		storageProvider.getProviderName() == 'nfs'
	}

	def "can list files in a directory"() {
		given:
		def dir = storageProvider['/karman-test']
		dir.save()
		dir['test.txt'].text("Hello From Spock!").save()
		dir['hello/testsub.txt'].text("Hello From Spock!").save()
		dir['test2.txt'].text("Hello From Spock!").save()
		when:
		def files = dir.listFiles()
		def subfiles = dir.listFiles(prefix:'hello/')
		println files
		then:
		files.size() > 0
		subfiles.size() == 1
		dir['hello/testsub.txt'].getText('UTF-8') == "Hello From Spock!"
		cleanup:
		dir['test.txt'].delete()
		dir['hello/testsub.txt'].delete()
		dir['test2.txt'].delete()
	}

	def "can write to a file"() {
		given:
		def dir = storageProvider['/karman-test']
		dir.save()
		when:
			CloudFile file = dir['test.txt'].text("Hello From Spock!")
			file.save()
		then:
			file.getText("UTF-8") == "Hello From Spock!"
		cleanup:
			file.delete()
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