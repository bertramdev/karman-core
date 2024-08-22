package com.bertramlabs.plugins.karman.azure

import groovy.util.logging.Slf4j
import java.util.UUID
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.azure.AzureBlobStorageProvider
import spock.lang.Specification

@Slf4j
class AzureFileSpec extends Specification {

	static AzureFileStorageProvider storageProvider
	static AzureShare share
	static AzureDirectory directory

	def setupSpec() {
		storageProvider = AzureBlobStorageProvider.create(
			provider:'azure',
			storageAccount:System.getProperty('azure.storageAccount'),
			storageKey:System.getProperty('azure.storageKey')
		)

		share = new AzureShare(name: 'karman-file-spec-test', provider: storageProvider, shareName: 'karman-file-spec-test')
		if(!share.exists()) {
			share.save()
		}

		directory = new AzureDirectory(name: getTestDirectoryName(), provider: storageProvider, shareName: 'karman-file-spec-test')
		if(!directory.exists()) {
			directory.save()
		}
	}

	def cleanupSpec() {
		share.delete()
	}

	def "create a file via getAt syntax with new share"() {
		setup:
		AzureFile cloudFile = storageProvider['my-new-share']['new-file']
		setBytesAndSave(cloudFile)
		
		expect:
		cloudFile.name == 'new-file'
		storageProvider['my-new-share']['new-file'].exists()

		cleanup:
		storageProvider['my-new-share'].delete()
	}

	def "create a file via getAt syntax... share level"() {
		setup:
		AzureFile cloudFile = storageProvider[share.name]['getat-syntax']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'getat-syntax'
		storageProvider[share.name]['getat-syntax'].exists()
	}

	def "create a file via getAt syntax... share/directory level"() {
		setup:
		AzureFile cloudFile = storageProvider[share.name]['subdir/getat-syntax']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'subdir/getat-syntax'
		storageProvider[share.name]['subdir/getat-syntax'].exists()
	}


	def "create a file via getAt syntax... share/directory level with spaces"() {
		setup:
		AzureFile cloudFile = storageProvider[share.name]['sub dir/getat syntax']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'sub dir/getat syntax'
		storageProvider[share.name]['sub dir/getat syntax'].exists()
	}

	def "create a file via getAt syntax... share/directory/directory level"() {
		setup:
		AzureFile cloudFile = storageProvider[share.name]['subdir2/another/getat-syntax']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'subdir2/another/getat-syntax'
		storageProvider[share.name]['subdir2/another/getat-syntax'].exists()
	}

	def "create a file via getAt syntax... share/directory/directory level for existing file"() {
		setup:
		AzureFile cloudFile = storageProvider[share.name]['subdir3/another/getat-syntax']
		setBytesAndSave(cloudFile)
		// Refetch it and save again
		cloudFile = storageProvider[share.name]['subdir3/another/getat-syntax']
		setBytesAndSave(cloudFile, 2048l)

		// Fetch again...
		cloudFile = storageProvider[share.name]['subdir3/another/getat-syntax']

		expect:
		cloudFile.name == 'subdir3/another/getat-syntax'
		cloudFile.exists()
		cloudFile.getContentLength() == 2048
	}

	def "exists for a file that does not exist"() {
		when:
		AzureFile cloudFile = directory.getFile('bogus-file')
		
		then:
		cloudFile.exists() == false
	}

	def "exists for a file in a non-existent directory"() {
		when:
		AzureFile cloudFile = storageProvider[share.name]['nonexistent/bogus-file']
		AzureFile anotherBogusCloudFile = share.getDirectory('karman-bogus').getFile('bogus-file')
		
		then:
		cloudFile.exists() == false
		anotherBogusCloudFile.exists() == false
	}

	def "getName"() {
		setup:
		AzureFile cloudFile = directory.getFile('get-name-test')
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == "${directory.name}/get-name-test"
		directory.getFile('get-name-test-another').name == "${directory.name}/get-name-test-another"
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
		AzureFile cloudFile = directory.getFile('large-test-file-blob')
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
		AzureFile cloudFile = directory.getFile('small-test-file-blob')
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
		AzureFile cloudFile = directory.getFile('get-url-test')
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
		AzureFile cloudFile = directory.getFile('get-url-test')
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

	def "delete a file"() {
		setup:
		// Create a 1024 kb file
		AzureFile cloudFile = directory.getFile('delete-test')
		setBytesAndSave(cloudFile)
		
		when:
		def deleteResult = cloudFile.delete()
		
		then:
		deleteResult == true
		cloudFile.exists() == false
	}

	def "delete a file that does not exist"() {
		setup:
		AzureFile cloudFile = directory.getFile('delete-test-does-not-exist')
		
		when:
		def deleteResult = cloudFile.delete()
		
		then:
		deleteResult == false
	}

	def "getInputStream for file"() {
		setup:
		File tempFile = File.createTempFile("temp",".tmp")
		tempFile << getOneKiloString()
		AzureFile cloudFile = directory.getFile('get-inputstream-test')
		cloudFile.setInputStream(new FileInputStream(tempFile))
		cloudFile.setContentLength(tempFile.size())
		def saveResult = cloudFile.save()

		when:
		AzureFile fileUnderTest = directory.getFile('get-inputstream-test')
		def fetchedFile = File.createTempFile("temp",".tmp")
		def fetchedFileOS = fetchedFile.newOutputStream()  
		fetchedFileOS << fileUnderTest.getInputStream()
		fetchedFileOS.close()

		then:
		getOneKiloString() == fetchedFile.text
	}

	def "copy a file and verify the status"() {
		setup:
		AzureFile cloudFile = directory.getFile('src-copy-file')
		setBytesAndSave(cloudFile)
		
		when:
		def copyResult = directory.getFile('tgt-copy-file').copy(cloudFile.getURL().toString())
		def count = 0
		def status = 'pending'
		while(count < 10 && status == 'pending') {
			status = directory.getFile('tgt-copy-file').getMetaAttribute('x-ms-copy-status')
			if(status != 'success') {
				sleep(5000)
			}
			count++
		}

		then:
		copyResult != null
		status == 'success'
		directory.getFile('tgt-copy-file').exists()
	}

	def "getUrl for file not saved"() {
		setup:
		AzureFile cloudFile = directory.getFile('get-url-test-not-saved2')
		
		expect:
		cloudFile.getURL().toString().contains('get-url-test-not-saved2')
	}

	def "copy a nested file and verify the status"() {
		setup:
		def nestedDirectory = directory.getDirectory('nested-dir2')
		nestedDirectory.save()
		AzureFile cloudFile = nestedDirectory.getFile('src-copy-file2')
		setBytesAndSave(cloudFile)
		
		when:
		def copyResult = directory.getFile('tgt-copy-file2').copy(cloudFile.getURL().toString())
		def count = 0
		def status = 'pending'
		while(count < 10 && status == 'pending') {
			status = directory.getFile('tgt-copy-file2').getMetaAttribute('x-ms-copy-status')
			if(status != 'success') {
				sleep(5000)
			}
			count++
		}

		then:
		copyResult != null
		status == 'success'
		directory.getFile('tgt-copy-file2').exists()
	}

	def "create a file with content type and other ms-headers"() {
		setup:
		def file = storageProvider[share.name]['context-test/test.txt'].text("Setting the text value").contentType("text/plain")
		file.setMetaAttribute('x-ms-content-md5', '0efe36e30fd85a8225c8f087f2e50287')
		file.save()
		AzureFile cloudFile = storageProvider[share.name]['context-test/test.txt']

		expect:
		cloudFile.name == 'context-test/test.txt'
		cloudFile.getContentType() == 'text/plain'
		cloudFile.getMetaAttribute('Content-MD5') == '0efe36e30fd85a8225c8f087f2e50287'
		cloudFile.exists()
	}

//	def "create a very large file using a local file"() {
//		setup:
//		File largeFile = new File('/Users/bob/Downloads/ubuntu-14_04-ubuntu-14_04_3-disk1.vmdk')
//		
//		when:
//		AzureFile cloudFile = directory.getFile('very-large')
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

	private setBytesAndSave(AzureFile file, size=1024l) {
		byte[] bytes = new byte[size];
		Arrays.fill( bytes, (byte) 3 );
		file.setBytes(bytes)
		file.save()
	}

	private getTestDirectoryName() {
		return "karman-azure-test"
	}

	private getOneKiloString() {
		return '''Lorem ipsum dolor sit amet, eum eu sanctus voluptatum assueverit, nulla splendide pro et. Nominati democritum cu vel, te clita virtute inermis nam, id praesent voluptatum mel. Et vel stet tantas repudiandae, no cum latine laoreet efficiantur. At mucius consetetur nec, ei ipsum iuvaret mea. Illum incorrupte est ne, mea in ferri impetus impedit. Habeo laoreet pericula at vim, tantas putent sea at.

Probo facer tibique usu in, wisi efficiantur ne nam, at semper aperiam vel. Utroque abhorreant an sit, et qui impedit oportere definitiones, ut persius accusam mei. Mel mazim mollis liberavisse cu. Id est tale rebum dicta, qui autem prodesset similique et, mea ne enim eius erroribus. Tation legere salutandi sed an, ad duo doctus singulis referrentur.

Accusamus pertinacia cu ius, pro vero congue meliore id. Nam dicant periculis ex. Hinc incorrupte cu duo, ea sumo paulo voluptatum vel. In eos vero audiam aperiri, vis ei probatus quaestio rationibus. Eu eos munere aperiri scripserit, unum primis sit id, te soluta noste'''
	}
}
