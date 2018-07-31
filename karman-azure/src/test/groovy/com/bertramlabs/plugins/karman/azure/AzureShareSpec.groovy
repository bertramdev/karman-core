package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Commons
import java.util.UUID
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureFileStorageProvider
import spock.lang.Specification

@Commons
class AzureShareSpec extends Specification {

	static AzureFileStorageProvider storageProvider

	def setupSpec() {
		storageProvider = AzureFileStorageProvider.create(
			provider:'azure',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey')
		)
	}

	def "create a share"() {
		when:
		def shareName = getTestDirectoryName()
		AzureShare share = new AzureShare(name: shareName, provider: storageProvider, shareName: shareName)
		def saveResult = share.save()
		def shares = storageProvider.getDirectories()
		def savedDirectory = shares.find { it.name == share.name }
	
		then:
		saveResult == true
		savedDirectory != null
		share.getName() == share.name
		share.isFile() == false
		share.isDirectory() == true
		share.exists() == true

		cleanup:
		savedDirectory.delete()
	}
	
	def "create a share with an invalid name"() {
		when:
		def shareName = 'INVALID*Name'
		AzureShare share = new AzureShare(name: shareName, provider: storageProvider, shareName: shareName)
		share.save()

		then:
		def e = thrown(Exception)
		e.message?.size() != 0
	}

	def "delete a share"() {
		setup:
		def shareName = getTestDirectoryName()
		AzureShare share = new AzureShare(name: shareName, provider: storageProvider, shareName: shareName)
		share.save()
		def shares = storageProvider.getDirectories()
		def savedShare = shares.find { it.name == share.name }
		assert(savedShare != null)

		when:
		def deleteResult = savedShare.delete()
		shares = storageProvider.getDirectories()
		def deletedDirectory = shares.find { it.name == share.name }

		then:
		deleteResult == true
		deletedDirectory == null
	}

	def "delete a share that does not exist"() {
		setup:
		def shareName = getTestDirectoryName()
		AzureShare share = new AzureShare(name: shareName, provider: storageProvider, shareName: shareName)
		
		when:
		share.delete()
		
		then:
		def e = thrown(Exception)
		e.message?.size() != 0
	}

	def "share exists for real directory"() {
		setup:
		def shareName = getTestDirectoryName()
		AzureShare share = new AzureShare(name: shareName, provider: storageProvider, shareName: shareName)
		share.save()
		AzureShare newDirectoryReference = new AzureShare(name: share.getName(), provider: storageProvider, shareName: share.getName())
		AzureShare bogusDirectory = new AzureShare(name: getTestDirectoryName(), provider: storageProvider)

		expect:
		share.exists()
		newDirectoryReference.exists()
		bogusDirectory.exists() == false

		cleanup:
		share.delete()
	}

	def "listFiles for empty share"() {
		setup:
		def shareName = getTestDirectoryName()
		AzureShare share = new AzureShare(name: shareName, provider: storageProvider, shareName: shareName)
		share.save()
		
		when:
		def files = share.listFiles()

		then:
		files.size() == 0
		
		cleanup:
		share.delete()
	}

	def "listFiles for share with files"() {
		setup:
		def shareName = getTestDirectoryName()
		AzureShare share = new AzureShare(name: shareName, provider: storageProvider, shareName: shareName)
		share.save()

		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzureFile cloudFile1 = share.getFile('file1')
		cloudFile1.setBytes(bytes)
		cloudFile1.save()

		AzureFile cloudFile2 = share.getFile('file2')
		cloudFile2.setBytes(bytes)
		cloudFile2.save()
		
		when:
		def files = share.listFiles()

		then:
		files.size() == 2
		files[0].name == 'file1'
		files[1].name == 'file2'
		
		cleanup:
		share.delete()
	}

	def "listFiles for share subdirectory with prefix"() {
		setup:
		def shareName = getTestDirectoryName()
		AzureShare share = new AzureShare(name: shareName, provider: storageProvider, shareName: shareName)
		share.save()

		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzureFile cloudFile1 = share.getFile('test2/file1')
		cloudFile1.setBytes(bytes)
		cloudFile1.save()

		AzureFile cloudFile2 = share.getFile('test2/file2')
		cloudFile2.setBytes(bytes)
		cloudFile2.save()

		when:
		def files = share.listFiles(prefix:'test2',delimiter:'/')

		then:
		files.size() == 2
		files[0].name == 'file1'
		files[1].name == 'file2'

		cleanup:
		share.delete()
	}

	// TODO : Handle delimiter and prefix

	private getTestDirectoryName() {
		return "karman-azure-file-test-${UUID.randomUUID().toString()}"
	}

}