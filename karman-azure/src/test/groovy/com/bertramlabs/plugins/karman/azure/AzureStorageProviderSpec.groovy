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