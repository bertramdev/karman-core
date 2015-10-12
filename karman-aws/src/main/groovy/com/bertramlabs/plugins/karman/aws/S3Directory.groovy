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
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory

class S3Directory extends Directory {

    String region = ''

    /**
     * Check if bucket exists
     * @return Boolean
     */
	Boolean exists() {
        s3Client.doesBucketExist(name)
	}

    /**
     * List bucket files
     * @param options (prefix, marker, delimiter and maxKeys)
     * @return List
     */
	List listFiles(options = [:]) {
        ListObjectsRequest request = new ListObjectsRequest(name, options?.prefix, options?.marker, options?.delimiter, options?.maxKeys)
        ObjectListing objectListing = s3Client.listObjects(request)
        objectListing.objectSummaries.collect { S3ObjectSummary summary -> cloudFileFromS3Object(summary) }
	}

    /**
     * Create bucket for a given region (default to region in config if not defined)
     * @return Bucket
     */
	def save() {
        if (region) {
            s3Client.createBucket(name, region)
        } else {
            s3Client.createBucket(name)
        }
	}

	CloudFile getFile(String name) {
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

    private AmazonS3Client getS3Client(String region = '') {
        provider.s3Client
    }

}