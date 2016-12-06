package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Commons
import java.util.UUID
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureFileStorageProvider
import spock.lang.Specification

@Commons
class AzureDirectorySpec extends Specification {

	static AzureFileStorageProvider storageProvider
	static AzureShare share

	def setupSpec() {
		storageProvider = AzureFileStorageProvider.create(
			provider:'azure',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey')
		)

		share = new AzureShare(name: 'karman-directory-spec-test', provider: storageProvider, shareName: 'karman-directory-spec-test')
		share.save()
	}

	def cleanupSpec() {
		share.delete()
	}

	def "create a directory"() {
		when:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider, shareName: share.name)
		def saveResult = directory.save()
		def share = storageProvider.getDirectory('karman-directory-spec-test')
		def directories = share.listFiles()
		def savedDirectory = directories?.find { it.name == directory.name }
	
		then:
		saveResult == true
		savedDirectory != null
		savedDirectory.getName() == directory.name
		savedDirectory.isFile() == false
		savedDirectory.isDirectory() == true
		savedDirectory.exists() == true
	}

	def "create nested directories"() {
		when:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider, shareName: share.name)
		directory.save()

		AzureDirectory nestedDirectory = new AzureDirectory(name: "${directory.name}/${getTestDirectoryName()}", provider: storageProvider, shareName: share.name)
		nestedDirectory.save()
		def share = storageProvider.getDirectory('karman-directory-spec-test')
		def directories = share.listFiles()
		def foundParentDir = directories?.find { it.name == directory.name }
		def subdirs = foundParentDir.listFiles()
		def foundSubDir = subdirs?.find { it.name == nestedDirectory.name }

		then:
		foundParentDir != null
		foundParentDir.exists() == true
		foundSubDir != null
		foundSubDir.exists() == true
	}
	
	def "create a directory with an invalid name"() {
		when:
		AzureDirectory directory = new AzureDirectory(name: 'INVALID*Name', provider: storageProvider, shareName: share.name)
		directory.save()

		then:
		def e = thrown(Exception)
		e.message?.size() != 0
	}

	def "delete a directory"() {
		setup:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider, shareName: share.name)
		directory.save()
		def directories = share.listFiles()
		def savedDirectory = directories.find { it.name == directory.name }
		assert(savedDirectory != null)

		when:
		def deleteResult = savedDirectory.delete()
		directories = share.listFiles()
		def deletedDirectory = directories.find { it.name == directory.name }

		then:
		deleteResult == true
		deletedDirectory == null
	}

	def "delete a directory that does not exist"() {
		setup:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider, shareName: share.name)
		
		when:
		directory.delete()
		
		then:
		def e = thrown(Exception)
		e.message?.size() != 0
	}

	def "directory exists for real directory"() {
		setup:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider, shareName: share.name)
		directory.save()
		AzureDirectory newDirectoryReference = new AzureDirectory(name: directory.getName(), provider: storageProvider, shareName: share.name)
		AzureDirectory bogusDirectory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider, shareName: share.name)

		expect:
		directory.exists()
		newDirectoryReference.exists()
		bogusDirectory.exists() == false

		cleanup:
		directory.delete()
	}

	def "listFiles for empty directory"() {
		setup:
		AzureDirectory directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider, shareName: share.name)
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
		def directoryName = getTestDirectoryName()
		AzureDirectory directory = new AzureDirectory(name: directoryName, provider: storageProvider, shareName: share.name)
		directory.save()

		AzureFile cloudFile1 = directory.getFile('file1')
		setBytesAndSave(cloudFile1)
		
		AzureFile cloudFile2 = directory.getFile('file2')
		setBytesAndSave(cloudFile2)
		
		when:
		def files = directory.listFiles()

		then:
		files.size() == 2
		files[0].name == "${directoryName}/file1"
		files[1].name == "${directoryName}/file2"
		
		cleanup:
		files[0].delete()
		files[1].delete()
		directory.delete()
	}	

	def "getFile with paths creates appropriate AzureFile"() {
		setup:
		AzureDirectory directory = new AzureDirectory(name: 'created-dir', provider: storageProvider, shareName: share.name)
		directory.save()

		when:
		def rootFile = share.getFile('blah.txt')
		def subFile = share.getFile('subdir/blah.txt')
		def relativeToDir = directory.getFile('blah.txt')
		def subRelativeToDir = directory.getFile('anothersubdir/deeper/blah.txt')

		then:
		rootFile.name == 'blah.txt'
		subFile.name == 'subdir/blah.txt'
		relativeToDir.name == 'created-dir/blah.txt'
		subRelativeToDir.name == 'created-dir/anothersubdir/deeper/blah.txt'
	}

	def "create file in a nested directories"() {
		when:
		def parentDirName = getTestDirectoryName()
		AzureDirectory directory = new AzureDirectory(name: parentDirName, provider: storageProvider, shareName: share.name)
		directory.save()

		def subDirName = getTestDirectoryName()
		AzureDirectory nestedDirectory = new AzureDirectory(name: "${parentDirName}/${subDirName}", provider: storageProvider, shareName: share.name)
		nestedDirectory.save()

		AzureFile cloudFile1 = nestedDirectory.getFile('file1')
		setBytesAndSave(cloudFile1)
		
		then:
		nestedDirectory.getFile('file1').exists()
	}

	def "listFiles with prefix and delimiter"() {
		when:
		AzureDirectory directory = new AzureDirectory(name: 'prefixTestParent', provider: storageProvider, shareName: share.name)
		directory.save()
		AzureDirectory nestedDirectory = new AzureDirectory(name: 'prefixTestParent/prefixTestChild', provider: storageProvider, shareName: share.name)
		nestedDirectory.save()
		AzureFile cloudFile = nestedDirectory.getFile('file1')
		setBytesAndSave(cloudFile)
		AzureFile cloudFile2 = nestedDirectory.getFile('file2')
		setBytesAndSave(cloudFile2)
		
		then:
		share.listFiles(prefix: 'prefixTestParent/prefixTestChild/').size() == 2
		share.listFiles(prefix: 'prefixTestParent/prefixTestChild').size() == 2
		share.listFiles(prefix: 'prefixTestParent|prefixTestChild|', delimiter: '|').size() == 2
		share.listFiles(prefix: 'prefixTestParent/bogus/').size() == 0
		share.listFiles(prefix: 'bogus/bogus/').size() == 0
		share.listFiles(prefix: 'bogus/').size() == 0
		directory.listFiles(prefix: 'prefixTestChild').size() == 2
		directory.listFiles(prefix: 'prefixTestChild', delimiter: '|').size() == 2
		nestedDirectory.listFiles(prefix: '/').size() == 2
	}	

	private setBytesAndSave(AzureFile file, size=1024l) {
		byte[] bytes = new byte[size];
		Arrays.fill( bytes, (byte) 3 );
		file.setBytes(bytes)
		file.save()
	}

	private getTestDirectoryName() {
		return "karman-azure-test-${UUID.randomUUID().toString()}"
	}

}