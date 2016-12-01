package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Commons
import java.util.UUID
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureStorageProvider
import spock.lang.Specification

@Commons
class AzurePageBlobSpec extends Specification {

	static AzureStorageProvider storageProvider
	static AzureContainer directory

	def setupSpec() {
		storageProvider = AzureStorageProvider.create(
			provider:'azure-pageblob',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey')
		)

		directory = new AzureContainer(name: getTestDirectoryName(), provider: storageProvider)
		if(!directory.exists()) {
			directory.save()
		}
	}

	def cleanupSpec() {
		directory.delete()
	}

	def "create a file via getAt syntax with new directory"() {
		setup:
		def dirName = 'karman-azure-test-2'
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzurePageBlobFile cloudFile = storageProvider[dirName]['getat-syntax']
		cloudFile.setBytes(bytes)
		cloudFile.save()

		expect:
		cloudFile.name == 'getat-syntax'
		storageProvider[dirName]['getat-syntax'].exists()

		cleanup:
		storageProvider[dirName].delete()
	}

	def "create a file via getAt syntax"() {
		setup:
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzurePageBlobFile cloudFile = storageProvider[getTestDirectoryName()]['getat-syntax']
		cloudFile.setBytes(bytes)
		cloudFile.save()

		expect:
		cloudFile.name == 'getat-syntax'
		storageProvider[getTestDirectoryName()]['getat-syntax'].exists()
	}

	def "exists for a file that does not exist"() {
		when:
		AzurePageBlobFile cloudFile = directory.getFile('bogus-file')
		
		then:
		cloudFile.exists() == false
	}

	def "exists for a file in a non-existent directory"() {
		when:
		AzurePageBlobFile cloudFile = storageProvider['karman-bogus']['bogus-file']
		AzurePageBlobFile anotherBogusCloudFile = storageProvider.getDirectory('karman-bogus').getFile('bogus-file')
		
		then:
		cloudFile.exists() == false
		anotherBogusCloudFile.exists() == false
	}

	def "getName"() {
		setup:
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzurePageBlobFile cloudFile = directory.getFile('get-name-test')
		cloudFile.setBytes(bytes)
		cloudFile.save()

		expect:
		cloudFile.name == 'get-name-test'
		directory.getFile('get-name-test-another').name == 'get-name-test-another'
	}
	
	def "create a file larger than chunk size"() {
		setup:
		// Create a file larger than 4MP
		File tempFile = File.createTempFile("temp",".tmp")
		def fileSizeKb = 5l * 1024l
		(0..fileSizeKb).each {
			tempFile << getOneKiloString()	
		}
		
		when:
		AzurePageBlobFile cloudFile = directory.getFile('large-test-file-blob')
		cloudFile.setInputStream(new FileInputStream(tempFile))
		cloudFile.setContentLength(tempFile.size())
		def saveResult = cloudFile.save()

		then:
		saveResult == true
		cloudFile.exists() == true
		cloudFile.getContentLength() == tempFile.size()
	}

	def "create a small file using a local file"() {
		setup:
		// Create a 1024 kb file
		File tempFile = File.createTempFile("temp",".tmp")
		tempFile << getOneKiloString()

		when:
		AzurePageBlobFile cloudFile = directory.getFile('small-test-file-blob')
		cloudFile.setInputStream(new FileInputStream(tempFile))
		cloudFile.setContentLength(tempFile.size())
		def saveResult = cloudFile.save()

		then:
		saveResult == true
		cloudFile.exists() == true
		cloudFile.isFile() == true
		cloudFile.isDirectory() == false
		cloudFile.getContentLength() == tempFile.size()
	}
	
	def "getUrl without expiration"() {
		setup:
		File tempFile = File.createTempFile("temp",".tmp")
		tempFile << getOneKiloString()

		when:
		AzurePageBlobFile cloudFile = directory.getFile('get-url-test')
		cloudFile.setInputStream(new FileInputStream(tempFile))
		cloudFile.setContentLength(tempFile.size())
		def saveResult = cloudFile.save()
		URL downloadURL = cloudFile.getURL()

		then:
		downloadURL.toString().contains(directory.name)
		downloadURL.toString().contains('get-url-test')
	}

	def "getUrl with expiration"() {
		setup:
		File tempFile = File.createTempFile("temp",".tmp")
		tempFile << getOneKiloString()

		when:
		AzurePageBlobFile cloudFile = directory.getFile('get-url-test')
		cloudFile.setInputStream(new FileInputStream(tempFile))
		cloudFile.setContentLength(tempFile.size())
		def saveResult = cloudFile.save()
		def expiration = new Date()
		use (groovy.time.TimeCategory) {
			expiration = expiration + 1.day
		}
		URL downloadURL = cloudFile.getURL(expiration)

		// Now fetch the file and compare it to what we expect
		def fetchedFile = File.createTempFile("temp",".tmp")
		def fetchedFileOS = fetchedFile.newOutputStream()  
		fetchedFileOS << downloadURL.openStream()  
		fetchedFileOS.close()

		then:
		downloadURL != null
		getOneKiloString() == fetchedFile.text
	}

	def "create a small file using bytes"() {
		setup:
		// Create a 1024 kb file
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );

		when:
		AzurePageBlobFile cloudFile = directory.getFile('small-test-byte-blob')
		cloudFile.setBytes(bytes)
		def saveResult = cloudFile.save()

		then:
		saveResult == true
		cloudFile.exists() == true
		cloudFile.getContentLength() == 1024
	}

	def "delete a file"() {
		setup:
		// Create a 1024 kb file
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzurePageBlobFile cloudFile = directory.getFile('delete-test')
		cloudFile.setBytes(bytes)
		def saveResult = cloudFile.save()

		when:
		def deleteResult = cloudFile.delete()
		
		then:
		deleteResult == true
		cloudFile.exists() == false
	}

	def "delete a file that does not exist"() {
		setup:
		AzurePageBlobFile cloudFile = directory.getFile('delete-test-does-not-exist')
		
		when:
		def deleteResult = cloudFile.delete()
		
		then:
		deleteResult == false
	}

	def "getInputStream for file"() {
		setup:
		File tempFile = File.createTempFile("temp",".tmp")
		tempFile << getOneKiloString()
		AzurePageBlobFile cloudFile = directory.getFile('get-inputstream-test')
		cloudFile.setInputStream(new FileInputStream(tempFile))
		cloudFile.setContentLength(tempFile.size())
		def saveResult = cloudFile.save()

		when:
		AzurePageBlobFile fileUnderTest = directory.getFile('get-inputstream-test')
		def fetchedFile = File.createTempFile("temp",".tmp")
		def fetchedFileOS = fetchedFile.newOutputStream()  
		fetchedFileOS << fileUnderTest.getInputStream()
		fetchedFileOS.close()

		then:
		getOneKiloString() == fetchedFile.text
	}

	def "copy a file and verify the status"() {
		setup:
		// Create a 1024 kb file
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzurePageBlobFile cloudFile = directory.getFile('src-copy-file')
		cloudFile.setBytes(bytes)
		def saveResult = cloudFile.save()

		when:
		def copyResult = directory.getFile('tgt-copy-file').copy(cloudFile.getURL().toString())
		def count = 0
		def copyCompletionTime
		while(count < 10 && copyCompletionTime == null) {
			copyCompletionTime = directory.getFile('tgt-copy-file').getMetaAttribute('x-ms-copy-completion-time')
			if(!copyCompletionTime) {
				sleep(5000)
			}
			count++
		}

		then:
		copyResult != null
		copyCompletionTime != null
		directory.getFile('tgt-copy-file').exists()
	}

	def "create a snapshot"() {
		setup:
		// Create a 1024 kb file
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		
		AzurePageBlobFile cloudFile = directory.getFile('snapshot-blob')
		cloudFile.setBytes(bytes)
		cloudFile.save()

		when:
		def snapshotDate = cloudFile.snapshot()

		then:
		snapshotDate != null
		cloudFile.snapshotExists(snapshotDate)
		cloudFile.exists()
	}

	def "delete a snapshot"() {
		setup:
		// Create a 1024 kb file
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		
		AzurePageBlobFile cloudFile = directory.getFile('snapshot-blob-delete')
		cloudFile.setBytes(bytes)
		cloudFile.save()
		def snapshotDate = cloudFile.snapshot()

		when:
		cloudFile.deleteSnapshot(snapshotDate)

		then:
		cloudFile.snapshotExists(snapshotDate) == false
		cloudFile.exists()
	}

	def "delete a blob that has a snapshot"() {
		setup:
		// Create a 1024 kb file
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		
		AzurePageBlobFile cloudFile = directory.getFile('delete-blob-with-snapshot')
		cloudFile.setBytes(bytes)
		cloudFile.save()
		def snapshotDate = cloudFile.snapshot()

		when:
		def deleteResult = cloudFile.delete()

		then:
		deleteResult == true
		cloudFile.exists() == false
	}

	def "delete only blob snapshots"() {
		setup:
		// Create a 1024 kb file
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		
		AzurePageBlobFile cloudFile = directory.getFile('delete-blob-with-snapshot')
		cloudFile.setBytes(bytes)
		cloudFile.save()
		def snapshot1 = cloudFile.snapshot()
		def snapshot2 = cloudFile.snapshot()
		assert(cloudFile.snapshotExists(snapshot1))
		assert(cloudFile.snapshotExists(snapshot2))

		when:
		def deleteResult = cloudFile.deleteSnapshots()

		then:
		cloudFile.snapshotExists(snapshot1) == false
		cloudFile.snapshotExists(snapshot2) == false
		deleteResult == true
		cloudFile.exists() == true
	}

	def "snapshot exists on invalid snapshotid"() {
		setup:
		// Create a 1024 kb file
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		
		AzurePageBlobFile cloudFile = directory.getFile('blob-without-snapshot')
		cloudFile.setBytes(bytes)
		cloudFile.save()

		when:
		def snapshotExists = cloudFile.snapshotExists('blah')

		then:
		snapshotExists == false
		cloudFile.exists()
	}

	def "copy a file from a snapshot"() {
		setup:
		byte[] bytes = new byte[1024];
		Arrays.fill( bytes, (byte) 3 );
		AzurePageBlobFile cloudFile = directory.getFile('src-copy-file-with-snapshot')
		cloudFile.setBytes(bytes)
		cloudFile.save()
		def snapshotId = cloudFile.snapshot()
		assert(snapshotId != null && snapshotId != false)

		when:
		def copyResult = directory.getFile('tgt-copy-file-from-snapshot').copy(cloudFile.getURL().toString(), snapshotId)
		def count = 0
		def copyCompletionTime
		while(count < 10 && copyCompletionTime == null) {
			copyCompletionTime = directory.getFile('tgt-copy-file-from-snapshot').getMetaAttribute('x-ms-copy-completion-time')
			if(!copyCompletionTime) {
				sleep(5000)
			}
			count++
		}

		then:
		copyResult != null
		copyCompletionTime != null
		directory.getFile('tgt-copy-file-from-snapshot').exists()
	}

	def "getUrl for file not saved"() {
		setup:
		AzurePageBlobFile cloudFile = directory.getFile('get-url-test-not-saved')
		
		expect:
		cloudFile.getURL().toString().contains('get-url-test-not-saved')
	}

//	def "create a very large file using a local file"() {
//		setup:
//		File largeFile = new File('/Users/bob/Downloads/ubuntu-14_04-ubuntu-14_04_3-disk1.vmdk')
//		
//		when:
//		AzurePageBlobFile cloudFile = directory.getFile('very-large')
//		cloudFile.setInputStream(new FileInputStream(largeFile))
//		cloudFile.setContentLength(largeFile.size())
//		def saveResult = cloudFile.save()
//
//		then:
//		saveResult == true
//		cloudFile.exists() == true
//		cloudFile.isFile() == true
//		cloudFile.isDirectory() == false
//		cloudFile.getContentLength() == largeFile.size()
//	}

	private getTestDirectoryName() {
		return "karman-azure-test"
	}

	private getOneKiloString() {
		return '''Lorem ipsum dolor sit amet, eum eu sanctus voluptatum assueverit, nulla splendide pro et. Nominati democritum cu vel, te clita virtute inermis nam, id praesent voluptatum mel. Et vel stet tantas repudiandae, no cum latine laoreet efficiantur. At mucius consetetur nec, ei ipsum iuvaret mea. Illum incorrupte est ne, mea in ferri impetus impedit. Habeo laoreet pericula at vim, tantas putent sea at.

Probo facer tibique usu in, wisi efficiantur ne nam, at semper aperiam vel. Utroque abhorreant an sit, et qui impedit oportere definitiones, ut persius accusam mei. Mel mazim mollis liberavisse cu. Id est tale rebum dicta, qui autem prodesset similique et, mea ne enim eius erroribus. Tation legere salutandi sed an, ad duo doctus singulis referrentur.

Accusamus pertinacia cu ius, pro vero congue meliore id. Nam dicant periculis ex. Hinc incorrupte cu duo, ea sumo paulo voluptatum vel. In eos vero audiam aperiri, vis ei probatus quaestio rationibus. Eu eos munere aperiri scripserit, unum primis sit id, te soluta noste'''
	}
}
