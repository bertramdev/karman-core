package com.bertramlabs.plugins.karman.google

import groovy.util.logging.Slf4j
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.*
import com.bertramlabs.plugins.karman.google.GoogleStorageProvider
import spock.lang.Specification

@Slf4j
class GoogleStorageProviderSpec extends Specification {

	static GoogleStorageProvider storageProvider

	def setupSpec() {
		storageProvider = StorageProvider.create(
			provider:'google',
			clientEmail:System.getProperty('google.clientEmail'),
			privateKey:System.getProperty('google.privateKey'),
			projectId:System.getProperty('google.projectId')
		)
	}

	def "storage provider creates"() {
		expect:
		storageProvider != null
	}

	def "getDirectories"() {
		when:
		def dirs = storageProvider.getDirectories()
		log.info "size of directories: ${dirs.size()}"
		
		then:
		dirs != null
	}

	def "getProviderName"() {
		expect:
		storageProvider.getProviderName() == 'google'
	}
}