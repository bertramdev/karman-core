package com.bertramlabs.plugins.karman.google

import groovy.util.logging.Slf4j

import java.text.SimpleDateFormat
import spock.lang.Specification

@Slf4j
class GoogleCloudFileSpec extends Specification {

	static GoogleStorageProvider storageProvider
	static GoogleCloudBucket testBucket
		
	def setupSpec() {
		storageProvider = GoogleStorageProvider.create(
				provider:'google',
				clientEmail:System.getProperty('google.clientEmail'),
				privateKey:System.getProperty('google.privateKey'),
				projectId:System.getProperty('google.projectId')
		)
		testBucket = new GoogleCloudBucket(name: 'karman-gogle-spec-test', provider: storageProvider)
		testBucket.save()
	}

	def cleanupSpec() {
		testBucket.delete()
	}

	def "create a file"() {
		setup:
		GoogleCloudFile cloudFile = storageProvider[testBucket.name]['new-file']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'new-file'
		storageProvider[testBucket.name]['new-file'].exists()
	}

	def "create a file via getAt syntax... bucket level"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['getat-syntax']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'getat-syntax'
		storageProvider[testBucket.name]['getat-syntax'].exists()
	}

	def "create a file via getAt syntax... bucket/directory level"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['subdir/getat-syntax']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'subdir/getat-syntax'
		storageProvider[testBucket.name]['subdir/getat-syntax'].exists()
	}

	def "create a file via getAt syntax... bucket/directory level with spaces"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['sub dir/getat syntax']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'sub dir/getat syntax'
		storageProvider[testBucket.name]['sub dir/getat syntax'].exists()
	}

	def "create a file via getAt syntax... bucket/directory/directory level"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['subdir2/another/getat-syntax']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'subdir2/another/getat-syntax'
		storageProvider[testBucket.name]['subdir2/another/getat-syntax'].exists()
	}

	def "create a file via getAt syntax... bucket/directory/directory level for existing file"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['subdir3/another/getat-syntax']
		setBytesAndSave(cloudFile)
		// Refetch it and save again
		cloudFile = storageProvider[testBucket.name]['subdir3/another/getat-syntax']
		setBytesAndSave(cloudFile, 2048l)

		// Fetch again...
		cloudFile = storageProvider[testBucket.name]['subdir3/another/getat-syntax']

		expect:
		cloudFile.name == 'subdir3/another/getat-syntax'
		cloudFile.exists()
		cloudFile.getContentLength() == 2048
	}

	def "create a file and default content-type to application/octet-stream"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['subdir2/another/default-content-type']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == 'subdir2/another/default-content-type'
		cloudFile.getContentType() == 'application/octet-stream'
	}

	def "exists for a file that does not exist"() {
		when:
		def cloudFile = storageProvider[testBucket.name].getFile('bogus-file')
		def cloudFile2 = storageProvider[testBucket.name]['bogus-file2']

		then:
		cloudFile.exists() == false
		cloudFile2.exists() == false
	}

	def "exists for a file in a non-existent directory"() {
		when:
		def cloudFile = storageProvider[testBucket.name]['nonexistent/bogus-file']

		then:
		cloudFile.exists() == false
	}

	def "getName"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['nonexistent/get-name-test']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.name == "nonexistent/get-name-test"
	}

	def "getLastModified"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['nonexistent/get-last-modified-test']
		setBytesAndSave(cloudFile)

		expect:
		cloudFile.dateModified instanceof Date
	}

	def "getUrl"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['sub dir/getat syntax/getUrl']
		setBytesAndSave(cloudFile)
		def cloudFileNotSaved = storageProvider[testBucket.name]['sub dir/getat syntax/getUrlNotSaved']

		expect:
		cloudFile.getURL().toString() == 'https://storage.googleapis.com/storage/v1/b/karman-gogle-spec-test/o/sub dir/getat syntax/getUrl'
		cloudFileNotSaved.getURL().toString() == 'https://storage.googleapis.com/storage/v1/b/karman-gogle-spec-test/o/sub dir/getat syntax/getUrlNotSaved'
	}

	def "getText and setText"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['getText']
		cloudFile.setText('here is some text')
		cloudFile.save()
		def getText = storageProvider[testBucket.name]['getText'].getText()

		expect:
		getText == 'here is some text'
	}

	def "getParent"(){
		setup:
		def cloudFile = storageProvider[testBucket.name]['subdir/getParent']
		setBytesAndSave(cloudFile)
		def cloudFile2 = storageProvider[testBucket.name]['getParent']
		setBytesAndSave(cloudFile2)
		def cloudFileUnsaved = storageProvider[testBucket.name]['subdir/getParentUnsaved']
		def cloudFileUnsaved2 = storageProvider[testBucket.name]['getParentUnsaved']

		expect:
		cloudFile.getParent().name == testBucket.name
		cloudFile2.getParent().name == testBucket.name
		cloudFileUnsaved.getParent().name == testBucket.name
		cloudFileUnsaved2.getParent().name == testBucket.name
	}

	def "metadata"(){
		setup:
		def cloudFile = storageProvider[testBucket.name]['metadata']
		cloudFile.setMetaAttribute('blah','test')
		setBytesAndSave(cloudFile)
		def getFile = storageProvider[testBucket.name]['metadata']

		expect:
		getFile.getMetaAttribute('blah') == 'test'
	}

	def "metadata update"(){
		setup:
		def cloudFile = storageProvider[testBucket.name]['metadataupdate']
		cloudFile.setMetaAttribute('blah','test')
		setBytesAndSave(cloudFile)
		def getFile = storageProvider[testBucket.name]['metadataupdate']
		getFile.setMetaAttribute('blah','test2')
		getFile.save()
		def finalGetFile = storageProvider[testBucket.name]['metadataupdate']

		expect:
		finalGetFile.getMetaAttribute('blah') == 'test2'
	}

	def "set and get file property"(){
		setup:

		def cloudFile = storageProvider[testBucket.name]['customTime']

		def now = new Date()
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(now)
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
		def customTime = dateFormat.format(calendar.getTime())
		cloudFile.setMetaAttribute('customTime', customTime)

		setBytesAndSave(cloudFile)
		def getFile = storageProvider[testBucket.name]['customTime']

		expect:
		getFile.getMetaAttribute('customTime') == customTime
	}

	def "delete a file"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['deleteFile']
		setBytesAndSave(cloudFile)
		assert cloudFile.exists()
		def deleteResult = cloudFile.delete()
		def refetchFile = storageProvider[testBucket.name]['deleteFile']

		expect:
		refetchFile.exists() == false
		deleteResult == true
	}

	def "delete a nested file with spaces in the name"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['folder1/folder with spaces/delete file with spaces']
		setBytesAndSave(cloudFile)
		assert storageProvider[testBucket.name]['folder1/folder with spaces/delete file with spaces'].exists()
		def deleteResult = cloudFile.delete()
		def refetchFile = storageProvider[testBucket.name]['folder1/folder with spaces/delete file with spaces']

		expect:
		refetchFile.exists() == false
		deleteResult == true
	}

	def "delete a file with spaces in the name"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['delete file with spaces']
		setBytesAndSave(cloudFile)
		assert cloudFile.exists()
		def deleteResult = cloudFile.delete()
		def refetchFile = storageProvider[testBucket.name]['delete file with spaces']

		expect:
		refetchFile.exists() == false
		deleteResult == true
	}

	def "delete a file that does not exist"() {
		setup:
		def cloudFile = storageProvider[testBucket.name]['delete-test-does-not-exist']

		when:
		def deleteResult = cloudFile.delete()

		then:
		deleteResult == false
	}

	def "create a small file using a local file"() {
		setup:
		// Create a 1024 kb file
		File tempFile = File.createTempFile("temp",".tmp")
		tempFile << getOneKiloString()

		when:
		def cloudFile = storageProvider[testBucket.name]['small-test-file-blob']
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

	def "create a file with chunk size larger than file size"() {
		setup:
		// Create a 1024 kb file
		File tempFile = File.createTempFile("temp",".tmp")
		tempFile << getOneKiloString()

		when:
		def cloudFile = storageProvider[testBucket.name]['chunk-small']
		storageProvider.chunkSize = 8l * 1024l * 1024l
		cloudFile.chunked = true
		cloudFile.setInputStream(new FileInputStream(tempFile))
		cloudFile.setContentLength(tempFile.size())
		def saveResult = cloudFile.save()
		def refetch = storageProvider[testBucket.name]['chunk-small']

		then:
		saveResult == true
		cloudFile.exists() == true
		cloudFile.isFile() == true
		cloudFile.isDirectory() == false
		cloudFile.getContentLength() == tempFile.size()
		refetch.exists() == true
		refetch.isFile() == true
		refetch.isDirectory() == false
		refetch.getContentLength() == tempFile.size()
	}

	def "create a file larger than chunk size"() {
		setup:
		File largeFile = new File('/Users/bob/Downloads/Screen Capture on 2019-10-06 at 18-12-45.mov')

		when:
		def cloudFile = storageProvider[testBucket.name]['very-large']
		storageProvider.chunkSize = 8l * 1024l * 1024l
		cloudFile.chunked = true
		cloudFile.setInputStream(new FileInputStream(largeFile))
		cloudFile.setContentLength(largeFile.size())
		def saveResult = cloudFile.save()
		def refetch = storageProvider[testBucket.name]['very-large']

		then:
		saveResult == true
		cloudFile.exists() == true
		cloudFile.isFile() == true
		cloudFile.isDirectory() == false
		cloudFile.getContentLength() == largeFile.size()
		refetch.exists() == true
		refetch.isFile() == true
		refetch.isDirectory() == false
		refetch.getContentLength() == largeFile.size()
	}

	private getOneKiloString() {
		return '''Lorem ipsum dolor sit amet, eum eu sanctus voluptatum assueverit, nulla splendide pro et. Nominati democritum cu vel, te clita virtute inermis nam, id praesent voluptatum mel. Et vel stet tantas repudiandae, no cum latine laoreet efficiantur. At mucius consetetur nec, ei ipsum iuvaret mea. Illum incorrupte est ne, mea in ferri impetus impedit. Habeo laoreet pericula at vim, tantas putent sea at.

Probo facer tibique usu in, wisi efficiantur ne nam, at semper aperiam vel. Utroque abhorreant an sit, et qui impedit oportere definitiones, ut persius accusam mei. Mel mazim mollis liberavisse cu. Id est tale rebum dicta, qui autem prodesset similique et, mea ne enim eius erroribus. Tation legere salutandi sed an, ad duo doctus singulis referrentur.

Accusamus pertinacia cu ius, pro vero congue meliore id. Nam dicant periculis ex. Hinc incorrupte cu duo, ea sumo paulo voluptatum vel. In eos vero audiam aperiri, vis ei probatus quaestio rationibus. Eu eos munere aperiri scripserit, unum primis sit id, te soluta noste'''
	}

	private setBytesAndSave(GoogleCloudFile file, size=1024l) {
		byte[] bytes = new byte[size];
		Arrays.fill( bytes, (byte) 3 );
		file.setBytes(bytes)
		file.save()
	}

	private getTestBucketName() {
		return "karman-gogle-test-${UUID.randomUUID().toString()}" // can't use 'google' in the name!
	}

}