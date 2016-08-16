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

	/* def "store a chunked file"() {
		setup:
		OpenstackStorageProvider storageProvider = OpenstackStorageProvider.create(
			provider:'openstack',
			//username:System.getProperty('openstack.username'),
			//apiKey:System.getProperty('openstack.apiKey'),
			//identityUrl:System.getProperty('openstack.identityUrl'),
			chunkSize:20l * 1024l * 1024l
		)
		def file = new File('/Users/jsaardchit/Downloads/Firefox_44.0.dmg')
		//def file = new File('/Users/jsaardchit/winshare/WEB2-MORPH-2.VHD')

		when:
		def cloudFile = storageProvider['testing']['chunked/Firefox_44_10.0.dmg']
		cloudFile.chunked = true
		cloudFile.writeStream = new FileInputStream(file)
		cloudFile.save()

		then:
		cloudFile.getURL()
	} */

	/* def "delete a file"() {
		setup:
		OpenstackStorageProvider storageProvider = OpenstackStorageProvider.create(
			provider:'openstack',
			//username:System.getProperty('openstack.username'),
			//apiKey:System.getProperty('openstack.apiKey'),
			//identityUrl:System.getProperty('openstack.identityUrl')
		)

		when:
		def cloudFile = storageProvider['testing']['WEB2-MORPH-0.VHD']
		cloudFile.delete()

		then:
		!cloudFile.exists()
	} */
}