/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package com.bertramlabs.plugins.karman.aws

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.Bucket
import com.amazonaws.services.s3.model.DeleteBucketRequest
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ListVersionsRequest
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.amazonaws.services.s3.model.S3VersionSummary
import com.amazonaws.services.s3.model.VersionListing
import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory

class S3Directory extends Directory<S3CloudFile> {

	Bucket bucket
    String region = ''

    /**
     * Check if bucket exists
     * @return Boolean
     */
	Boolean exists() {
        s3Client.doesBucketExist(name)
	}

	String getRegion() {
		if(!region) {
			region = s3Client.getBucketLocation(name)
		}
		return region;
	}

    /**
     * List bucket files
     * @param options (prefix, marker, delimiter and maxKeys)
     * @return List
     */
	List<S3CloudFile> listFiles(Map<String,Object> options = [:]) {
        ListObjectsRequest request = new ListObjectsRequest(name, options?.prefix, options?.marker, options?.delimiter, options?.maxKeys)
        ObjectListing objectListing = s3Client.listObjects(request)
		def files = []
		if(options.delimiter) {
			def prefixes = []
			objectListing.commonPrefixes?.each { String prefix ->
				if(prefix != options.prefix) {
                    if(options.prefix) {
                        prefixes << options.prefix + prefix.substring(options.prefix.length()).split(options.delimiter)[0]    
                    } else {
                    	def prefixArgs = prefix.split(options.delimiter)
                    	if(prefixArgs) {
                    		prefixes << prefixArgs[0]
                    	}
                    }
				}
			}
			prefixes.unique()
			prefixes?.each { String prefix ->
				files << cloudFileFromPrefix(prefix)
			}


			objectListing.objectSummaries?.each { S3ObjectSummary summary ->
				if(summary.key != options.prefix || !options.prefix.endsWith(options.delimiter)) {
					files << cloudFileFromS3Object(summary)
				}
			}
		} else {
			files += objectListing.objectSummaries.collect { S3ObjectSummary summary -> cloudFileFromS3Object(summary) }
		}
		return files
	}

    /**
     * Create bucket for a given region (default to region in config if not defined)
     * @return Bucket
     */
	void save() {
        if (region) {
            s3Client.createBucket(name, region)
        } else {
            s3Client.createBucket(name)
        }
	}

	void delete() {
		ObjectListing objectListing = s3Client.listObjects(name);
		while (true) {
			Iterator<S3ObjectSummary> objIter = objectListing.getObjectSummaries().iterator();
			while (objIter.hasNext()) {
				s3Client.deleteObject(name, objIter.next().getKey());
			}

			// If the bucket contains many objects, the listObjects() call
			// might not return all of the objects in the first listing. Check to
			// see whether the listing was truncated. If so, retrieve the next page of objects
			// and delete them.
			if (objectListing.isTruncated()) {
				objectListing = s3Client.listNextBatchOfObjects(objectListing);
			} else {
				break;
			}
		}

		// Delete all object versions (required for versioned buckets).
		VersionListing versionList = s3Client.listVersions(new ListVersionsRequest().withBucketName(name));
		while (true) {
			Iterator<S3VersionSummary> versionIter = versionList.getVersionSummaries().iterator();
			while (versionIter.hasNext()) {
				S3VersionSummary vs = versionIter.next();
				s3Client.deleteVersion(name, vs.getKey(), vs.getVersionId());
			}

			if (versionList.isTruncated()) {
				versionList = s3Client.listNextBatchOfVersions(versionList);
			} else {
				break;
			}
		}
		s3Client.deleteBucket(name)
	}

	S3CloudFile getFile(String name) {
		new S3CloudFile(
                provider: provider,
                parent: this,
                name: name
        )
	}

    // PRIVATE

    private S3CloudFile cloudFileFromS3Object(S3ObjectSummary summary) {
        new S3CloudFile(
                provider: provider,
                parent: this,
                name: summary.key,
                summary: summary,
                existsFlag: true
        )
    }


	// PRIVATE

	private S3CloudPrefix cloudFileFromPrefix(String prefix) {
		new S3CloudPrefix(
			provider: provider,
			parent: this,
			name: prefix
		)
	}

    private AmazonS3Client getS3Client(String region = '') {
        provider.s3Client
    }

}