package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Commons
import java.util.UUID
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureBlobStorageProvider
import spock.lang.Specification

@Commons
class AzureContainerSpec extends Specification {

	static AzureBlobStorageProvider storageProvider

	def setupSpec() {
		storageProvider = AzureBlobStorageProvider.create(
			provider:'azure-pageblob',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey')
		)
	}

	def "create a directory"() {
		when:
		AzureContainer directory = new AzureContainer(name: getTestDirectoryName(), provider: storageProvider)
		def saveResult = directory.save()
		def directories = storageProvider.getDirectories()
		def savedDirectory = directories.find { it.name == directory.name }
	
		then:
		saveResult == true
		savedDirectory != null
		directory.getName() == directory.name
		directory.isFile() == false
		directory.isDirectory() == true
		directory.exists() == true

		cleanup:
		savedDirectory.delete()
	}
	
	def "create a directory with an invalid name"() {
		when:
		AzureContainer directory = new AzureContainer(name: 'INVALID*Name', provider: storageProvider)
		directory.save()

		then:
		def e = thrown(Exception)
		e.message?.size() != 0
	}

	def "delete a directory"() {
		setup:
		AzureContainer directory = new AzureContainer(name: getTestDirectoryName(), provider: storageProvider)
		directory.save()
		def directories = storageProvider.getDirectories()
		def savedDirectory = directories.find { it.name == directory.name }
		assert(savedDirectory != null)

		when:
		def deleteResult = savedDirectory.delete()
		directories = storageProvider.getDirectories()
		def deletedDirectory = directories.find { it.name == directory.name }

		then:
		deleteResult == true
		deletedDirectory == null
	}

	def "delete a directory that does not exist"() {
		setup:
		AzureContainer directory = new AzureContainer(name: getTestDirectoryName(), provider: storageProvider)
		
		when:
		directory.delete()
		
		then:
		def e = thrown(Exception)
		e.message?.size() != 0
	}

	def "directory exists for real directory"() {
		setup:
		AzureContainer directory = new AzureContainer(name: getTestDirectoryName(), provider: storageProvider)
		directory.save()
		AzureContainer newDirectoryReference = new AzureContainer(name: directory.getName(), provider: storageProvider)
		AzureContainer bogusDirectory = new AzureContainer(name: getTestDirectoryName(), provider: storageProvider)

		expect:
		directory.exists()
		newDirectoryReference.exists()
		bogusDirectory.exists() == false

		cleanup:
		directory.delete()
	}

	def "listFiles for empty directory"() {
		setup:
		AzureContainer directory = new AzureContainer(name: getTestDirectoryName(), provider: storageProvider)
		directory.save()
		
		when:
		def files = directory.listFiles()

		then:
		files.size() == 0
		
		cleanup:
		directory.delete()
	}

	def "listFiles for directory with files"() {
		setup:
		AzureContainer directory = new AzureContainer(name: getTestDirectoryName(), provider: storageProvider)
		directory.save()

		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzurePageBlobFile cloudFile1 = directory.getFile('file1')
		cloudFile1.setBytes(bytes)
		cloudFile1.save()

		AzurePageBlobFile cloudFile2 = directory.getFile('file2')
		cloudFile2.setBytes(bytes)
		cloudFile2.save()
		
		when:
		def files = directory.listFiles()

		then:
		files.size() == 2
		files[0].name == 'file1'
		files[1].name == 'file2'
		
		cleanup:
		directory.delete()
	}

	private getTestDirectoryName() {
		return "karman-azure-test-${UUID.randomUUID().toString()}"
	}

}