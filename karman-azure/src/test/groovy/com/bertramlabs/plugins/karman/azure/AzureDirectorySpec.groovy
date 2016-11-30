package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Commons
import java.util.UUID
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureStorageProvider
import spock.lang.Specification

@Commons
class AzureDirectorySpec extends Specification {

	static AzureStorageProvider storageProvider

	def setupSpec() {
		storageProvider = AzureStorageProvider.create(
			provider:'azure-pageblob',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey')
		)
	}

	def "create a directory"() {
		when:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider)
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
		AzureDirectory directory = new AzureDirectory(name: 'INVALID*Name', provider: storageProvider)
		directory.save()

		then:
		def e = thrown(Exception)
		e.message?.size() != 0
	}

	def "delete a directory"() {
		setup:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider)
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
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider)
		
		when:
		directory.delete()
		
		then:
		def e = thrown(Exception)
		e.message?.size() != 0
	}

	def "directory exists for real directory"() {
		setup:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider)
		directory.save()
		AzureDirectory newDirectoryReference = new AzureDirectory(name: directory.getName(), provider: storageProvider)
		AzureDirectory bogusDirectory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider)

		expect:
		directory.exists()
		newDirectoryReference.exists()
		bogusDirectory.exists() == false

		cleanup:
		directory.delete()
	}

	def "listFiles for empty directory"() {
		setup:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider)
		directory.save()
		
		when:
		def files = directory.listFiles()

		then:
		files.size() == 0
		
		cleanup:
		directory.delete()
	}

	private getTestDirectoryName() {
		return "karman-azure-test-${UUID.randomUUID().toString()}"
	}

}