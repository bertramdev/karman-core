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
import com.amazonaws.services.s3.Headers
import com.amazonaws.services.s3.model.*
import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.util.ChunkedInputStream

class S3CloudPrefix extends CloudFile {

	S3Directory parent

	@Override
	InputStream getInputStream() {
		return null
	}

	@Override
	void setInputStream(InputStream is) {

	}

	@Override
	OutputStream getOutputStream() {
		return null
	}

	@Override
	String getText(String encoding=null) {
		return null
	}


	@Override
	byte[] getBytes() {
		return new byte[0]
	}

	@Override
	void setText(String text) {

	}

	@Override
	void setBytes(Object bytes) {

	}

	@Override
	Long getContentLength() {
		return null
	}

	@Override
	String getContentType() {
		return null
	}

	@Override
	Date getDateModified() {
		return null
	}

	@Override
	void setContentType(String contentType) {

	}

	@Override
	Boolean exists() {
		return true
	}

	@Override
	Boolean isFile() {
		return false
	}

	@Override
	Boolean isDirectory() {
		return true
	}

	def save(acl) {
		throw new Exception("Not Implemented for a directory")
	}

	@Override
	def delete() {
		def dirName = name
		if(!name.endsWith('/')) {
			dirName = name + '/'
		}

		def maxKeys = 100
		def marker = null
		ListObjectsRequest request = new ListObjectsRequest(parent.name, dirName, null, null, maxKeys)

		ObjectListing objectListing = s3Client.listObjects(request)
		def keys = objectListing.objectSummaries?.collect { summary -> summary.key}
		if(keys) {
			DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(parent.name)
			deleteRequest.setKeys(keys.collect {new DeleteObjectsRequest.KeyVersion(it)})
			s3Client.deleteObjects(deleteRequest)
		}
		while(keys?.size() == maxKeys) {
			marker = keys.last()
			request = new ListObjectsRequest(parent.name, dirName, marker, null, maxKeys)

			objectListing = s3Client.listObjects(request)
			keys = objectListing.objectSummaries?.collect { summary -> summary.key}

			if(keys) {
				DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(parent.name)
				deleteRequest.setKeys(keys.collect {new DeleteObjectsRequest.KeyVersion(it)})
				s3Client.deleteObjects(deleteRequest)
			}
		}
		return true
	}

	@Override
	void setMetaAttribute(Object key, Object value) {

	}

	@Override
	def getMetaAttribute(Object key) {
		return null
	}

	@Override
	def getMetaAttributes() {
		return null
	}

	@Override
	void removeMetaAttribute(Object key) {

	}

	private AmazonS3Client getS3Client() {
		parent.provider.s3Client
	}
}