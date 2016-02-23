package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.openstack.OpenstackStorageProvider
import spock.lang.Specification

class OpenstackStorageProviderSpec extends Specification {
	def "storage provider creates"() {
		when:
		OpenstackStorageProvider storageProvider = OpenstackStorageProvider.create(
			provider:'openstack',
			username:System.getProperty('openstack.username'),
			apiKey:System.getProperty('openstack.apiKey'),
			identityUrl:System.getProperty('openstack.identityUrl')
		)

		then:
		storageProvider
	}

	/*
	def "store a chunked file"() {
		setup:
		OpenstackStorageProvider storageProvider = OpenstackStorageProvider.create(
			provider:'openstack',
			//username:System.getProperty('openstack.username'),
			//apiKey:System.getProperty('openstack.apiKey'),
			//identityUrl:System.getProperty('openstack.identityUrl'),
			chunkSize:20l * 1024l * 1024l
		)
		def file = new File('/Users/jsaardchit/Downloads/Firefox_44.0.dmg')

		when:
		def cloudFile = storageProvider['container']['Firefox_44.0.dmg']
		cloudFile.chunked = true
		cloudFile.writeStream = new FileInputStream(file)
		cloudFile.save()

		then:
		cloudFile.getURL()
	}
	*/
}