package com.bertramlabs.plugins.karman.alibaba

import com.aliyun.oss.OSSClient
import com.aliyun.oss.model.ListObjectsRequest
import com.aliyun.oss.model.OSSObjectSummary
import com.aliyun.oss.model.ObjectListing
import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import groovy.util.logging.Slf4j

@Slf4j
class AlibabaDirectory extends Directory<AlibabaCloudFile> {

	String region = ''

	/**
	 * Check if bucket exists
	 * @return Boolean
	 */
	Boolean exists() {
		getOSSClient().doesBucketExist(name)
	}

	/**
	 * List bucket files
	 * @param options (prefix, marker, delimiter and maxKeys)
	 * @return List
	 */
	List<AlibabaCloudFile> listFiles(Map<String,Object> options = [:]) {
		ListObjectsRequest request = new ListObjectsRequest()
		request.setBucketName(name)
		request.setPrefix(options?.prefix)
		request.setDelimiter(options?.delimiter)
		request.setMarker(options?.marker)
		if(options?.maxKeys) {
			request.setMaxKeys(options?.maxKeys)
		}

		ObjectListing objectListing = getOSSClient().listObjects(request)

		def files = []
		if(options.delimiter) {
			def prefixes = []
			objectListing.commonPrefixes?.each { String prefix ->
				if(prefix != options.prefix) {
					if(options.prefix) {
						prefixes << options.prefix + prefix.substring(options.prefix.length()).split(options.delimiter)[0]
					} else {
						prefixes << prefix.split(options.delimiter)[0]
					}

				}
			}
			prefixes.unique()
			prefixes?.each { String prefix ->
				files << cloudFileFromPrefix(prefix)
			}


			objectListing.objectSummaries?.each { OSSObjectSummary summary ->
				if(summary.key != options.prefix || !options.prefix.endsWith(options.delimiter)) {
					files << cloudFileFromOSSObject(summary)
				}
			}
		} else {
			files += objectListing.objectSummaries.collect { OSSObjectSummary summary -> cloudFileFromOSSObject(summary) }
		}
		return files
	}

	/**
	 * Create bucket for a given region (default to region in config if not defined)
	 * @return Bucket
	 */
	void save() {
		if (region) {
			getOSSClient().createBucket(name, region)
		} else {
			getOSSClient().createBucket(name)
		}
	}

	void delete() {
		getOSSClient().deleteBucket(name)
	}

	AlibabaCloudFile getFile(String name) {
		new AlibabaCloudFile(
			provider: provider,
			parent: this,
			name: name
		)
	}

	// PRIVATE

	private AlibabaCloudFile cloudFileFromOSSObject(OSSObjectSummary summary) {
		new AlibabaCloudFile(
			provider: provider,
			parent: this,
			name: summary.key,
			summary: summary,
			existsFlag: true
		)
	}


	// PRIVATE

	private AlibabaCloudPrefix cloudFileFromPrefix(String prefix) {
		new AlibabaCloudPrefix(
			provider: provider,
			parent: this,
			name: prefix
		)
	}

	private OSSClient getOSSClient(String region='') {
		((AlibabaStorageProvider)provider).getOSSClient()
	}


}