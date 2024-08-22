package com.bertramlabs.plugins.karman.google

import groovy.util.logging.Slf4j
import java.util.UUID
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.google.GoogleStorageProvider
import spock.lang.Specification

@Slf4j
class GoogleCloudBucketSpec extends Specification {

	static GoogleStorageProvider storageProvider
	static GoogleCloudBucket testBucket

	def setupSpec() {
		storageProvider = GoogleStorageProvider.create(
				provider:'google',
				clientEmail:System.getProperty('google.clientEmail'),
				privateKey:System.getProperty('google.privateKey'),
				projectId:System.getProperty('google.projectId')
		)
		testBucket = new GoogleCloudBucket(name: getTestBucketName(), provider: storageProvider)
		testBucket.save()
	}

	def cleanupSpec() {
		testBucket.delete()
	}

	def "create a bucket"() {
		when:
		def name = getTestBucketName()
		GoogleCloudBucket directory = new GoogleCloudBucket(name: name, provider: storageProvider)
		def saveResult = directory.save()
		def dir = storageProvider.getDirectory(name)

		then:
		saveResult == true
		dir != null
		dir.getName() == name
		dir.isFile() == false
		dir.isDirectory() == true
		dir.exists() == true

		cleanup:
		directory.delete()
	}

	def "create a bucket with standard format"() {
		when:
		def name = getTestBucketName()
		GoogleCloudBucket directory = storageProvider[name]
		def saveResult = directory.save()
		def dir = storageProvider.getDirectory(name)

		then:
		saveResult == true
		dir != null
		dir.getName() == name
		dir.isFile() == false
		dir.isDirectory() == true
		dir.exists() == true

		cleanup:
		directory.delete()
	}

	def "delete a bucket"() {
		setup:
		def name = getTestBucketName()
		GoogleCloudBucket directory = storageProvider[name]
		directory.save()

		when:
		def deleteResult = directory.delete()
		GoogleCloudBucket refetchedDir = storageProvider[name]

		then:
		deleteResult == true
		refetchedDir.exists() == false
	}

	def "save a bucket after it already exists"() {
		setup:
		def name = getTestBucketName()
		GoogleCloudBucket directory = storageProvider[name]
		directory.save()

		when:
		def saveAgainResults = directory.save()

		then:
		saveAgainResults == true
		directory.exists() == true

		cleanup:
		directory.delete()
	}

	def "create a bucket with an invalid name"() {
		when:
		GoogleCloudBucket bucket = storageProvider['google'] // can't use google in the name
		def results = bucket.save()

		then:
		results == false
	}

	def "delete a bucket that does not exist"() {
		setup:
		GoogleCloudBucket bucket = storageProvider[getTestBucketName()]

		when:
		def results = bucket.delete()

		then:
		results == false
	}

	def "listFiles for bucket with files"() {
		setup:
		def bucketName = getTestBucketName()
		GoogleCloudBucket bucket = storageProvider[bucketName]
		bucket.save()

		def cloudFile1 = bucket.getFile('file1')
		setBytesAndSave(cloudFile1)

		def cloudFile2 = bucket.getFile('file2')
		setBytesAndSave(cloudFile2)

		when:
		def files = bucket.listFiles()

		then:
		files.size() == 2
		files[0].name == "file1"
		files[1].name == "file2"

		cleanup:
		files[0].delete()
		files[1].delete()
		bucket.delete()
	}

	def "listFiles for empty bucket"() {
		setup:
		def bucketName = getTestBucketName()
		GoogleCloudBucket bucket = storageProvider[bucketName]
		bucket.save()

		when:
		def files = bucket.listFiles()

		then:
		files.size() == 0

		cleanup:
		bucket.delete()
	}

	def "listFiles with complex tree"() {
		setup:
		def bucketName = getTestBucketName()
		GoogleCloudBucket bucket = storageProvider[bucketName]
		bucket.save()

		// Creating the following structure
		createFile(bucket, 'file1')
		createFile(bucket, 'folder2/file2')
		createFile(bucket, 'file3')
		createFile(bucket, 'folder3/file4')
		createFile(bucket, 'folder3/file5')
		createFile(bucket, 'folder3/folder4/file6')
		createFile(bucket, 'folder3/folder6/file7')

		expect:
		bucket.listFiles().size() == 7
		bucket.listFiles(prefix: '').size() == 7
		bucket.listFiles(prefix: 'folder').size() == 5
		bucket.listFiles(prefix: 'folder', delimiter: '/').size() == 2
		bucket.listFiles(prefix: 'folder', delimiter: '/')[0] instanceof GoogleCloudDirectory
		bucket.listFiles(prefix: 'folder2/', delimiter: '/').size() == 1
		bucket.listFiles(prefix: 'folder3/', delimiter: '/').size() == 4 // 2 files and 2 directories
		bucket.listFiles(prefix: 'folder3/', delimiter: '/').findAll { it instanceof GoogleCloudDirectory }.size() == 2
		bucket.listFiles(prefix: 'prefixTestParent/bogus/').size() == 0
		bucket.listFiles(prefix: 'bogus/bogus/').size() == 0
		bucket.listFiles(prefix: 'bogus/').size() == 0

		cleanup:
		bucket.delete()
	}

	def "delete a bucket with files"() {
		setup:
		def bucketName = getTestBucketName()
		GoogleCloudBucket bucket = storageProvider[bucketName]
		bucket.save()
		createFile(bucket, 'file1')
		createFile(bucket, 'folder2/file2')
		bucket.delete()

		expect:
		storageProvider[bucketName].exists() == false
	}

	def "delete a bucket without files"() {
		setup:
		def bucketName = getTestBucketName()
		GoogleCloudBucket bucket = storageProvider[bucketName]
		bucket.save()
		bucket.delete()

		expect:
		storageProvider[bucketName].exists() == false
	}

	def "delete a nested folder with files"() {
		setup:
		def bucketName = getTestBucketName()
		GoogleCloudBucket bucket = storageProvider[bucketName]
		bucket.save()
		createFile(bucket, 'file1')
		createFile(bucket, 'folder2/file2')
		createFile(bucket, 'folder3/file3')
		createFile(bucket, 'folder3/folder4/file4')
		assert bucket.listFiles().size() == 4
		assert bucket.listFiles(prefix: 'folder2/', delimiter: '/').size() == 1
		assert bucket.listFiles(prefix: 'folder3/', delimiter: '/').size() == 2
		def folder3 = bucket.listFiles(prefix: '', delimiter: '/').find { it.name == 'folder3' }
		folder3.delete()

		expect:
		assert bucket.listFiles().size() == 2
		assert bucket.listFiles(prefix: 'folder2/', delimiter: '/').size() == 1
		assert bucket.listFiles(prefix: 'folder3/', delimiter: '/').size() == 0

		cleanup:
		bucket.delete()
	}

	def "create a bucket in particular region"(){
		when:
		def name = getTestBucketName()
		GoogleCloudBucket directory = new GoogleCloudBucket(name: name, provider: storageProvider, location: 'US-EAST4')
		def saveResult = directory.save()
		def dir = storageProvider.getDirectory(name)

		then:
		saveResult == true
		dir != null
		dir.getName() == name
		dir.isFile() == false
		dir.isDirectory() == true
		dir.exists() == true
		dir.location == 'US-EAST4'
		dir.locationType == 'region'

		cleanup:
		directory.delete()
	}

	def "create a bucket in particular dual-region"(){
		when:
		def name = getTestBucketName()
		GoogleCloudBucket directory = new GoogleCloudBucket(name: name, provider: storageProvider, location: 'NAM4')
		def saveResult = directory.save()
		def dir = storageProvider.getDirectory(name)

		then:
		saveResult == true
		dir != null
		dir.getName() == name
		dir.isFile() == false
		dir.isDirectory() == true
		dir.exists() == true
		dir.location == 'NAM4'
		dir.locationType == 'dual-region'

		cleanup:
		directory.delete()
	}

	def "create a bucket in particular multi-region"(){
		when:
		def name = getTestBucketName()
		GoogleCloudBucket directory = new GoogleCloudBucket(name: name, provider: storageProvider, location: 'US')
		def saveResult = directory.save()
		def dir = storageProvider.getDirectory(name)

		then:
		saveResult == true
		dir != null
		dir.getName() == name
		dir.isFile() == false
		dir.isDirectory() == true
		dir.exists() == true
		dir.location == 'US'
		dir.locationType == 'multi-region'

		cleanup:
		directory.delete()
	}

	def "create a bucket with storage class"() {
		when:
		def name = getTestBucketName()
		GoogleCloudBucket directory = new GoogleCloudBucket(name: name, provider: storageProvider, storageClass: 'ARCHIVE')
		def saveResult = directory.save()
		def dir = storageProvider.getDirectory(name)

		then:
		saveResult == true
		dir != null
		dir.getName() == name
		dir.isFile() == false
		dir.isDirectory() == true
		dir.exists() == true
		dir.storageClass == 'ARCHIVE'

		cleanup:
		directory.delete()
	}

	def "modify a bucket's storage class"() {
		when:
		def name = getTestBucketName()
		GoogleCloudBucket directory = new GoogleCloudBucket(name: name, provider: storageProvider, storageClass: 'STANDARD')
		def saveResult = directory.save()
		def dir = storageProvider.getDirectory(name)
		dir.storageClass = 'ARCHIVE'
		def updateResult = dir.save()

		then:
		saveResult == true
		updateResult == true
		dir != null
		dir.getName() == name
		dir.isFile() == false
		dir.isDirectory() == true
		dir.exists() == true
		dir.storageClass == 'ARCHIVE'

		cleanup:
		directory.delete()
	}

	def "buckets listed must already have metaDataLoaded"() {
		when:
		def name = getTestBucketName()
		GoogleCloudBucket directory = new GoogleCloudBucket(name: name, provider: storageProvider)
		directory.save()
		def dirs = storageProvider.getDirectories()
		def dir = dirs.find { it.name == directory.name}

		then:
		dir.metaDataLoaded == true

		cleanup:
		directory.delete()
	}

	private createFile(GoogleCloudBucket bucket, String name) {
		def cloudFile = storageProvider[bucket.name][name]
		setBytesAndSave(cloudFile)
		return cloudFile
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