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
 
package com.bertramlabs.plugins.karman.google

import groovy.util.logging.Commons

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory

@Commons
class GoogleCloudBucket extends Directory {

	private String locationType
	private String location
	private String storageClass
	private Boolean metaDataLoaded = false

	public GoogleCloudBucket(options) {
		this.locationType = options.locationType ?: locationType
		this.location = options.location ?: location
		this.storageClass = options.storageClass ?: storageClass
		this.name = options.name ?: name
		this.provider = options.provider ?: provider
		this.metaDataLoaded = options.metaDataLoaded ?: metaDataLoaded
	}

	void setLocationType(locationType) {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		this.locationType = locationType
	}

	String getLocationType() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		return locationType
	}

	void setLocation(location) {
		log.trace "setLocation: ${location}"
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		this.location = location
	}

	String getLocation() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		return location
	}

	void setStorageClass(storageClass) {
		log.trace "setStorageClass: ${storageClass}"
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		this.storageClass = storageClass
	}

	String getStorageClass() {
		if(!metaDataLoaded) {
			loadObjectMetaData()
		}
		return storageClass
	}

	Boolean exists() {
		log.debug "exists: ${name}"
		GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider
		def path = "storage/v1/b/${name}"
		def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, [:], 'GET')
		return results.success
	}

	List listFiles(options = [:]) {
		log.debug "listFiles: ${name} options:${options}"
		GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider
		def path = "storage/v1/b/${name}/o"
		def keepGoing = true
		def nextPageToken
		def files = []
		def thisDirectory = this
		while(keepGoing) {
			def requestOpts = [query: [
					prefix                  : options.prefix,
					maxResults              : options.maxResults ?: 1000,
					startOffset             : options.startOffset,
					endOffset               : options.endOffset,
					versions                : options.versions != null ? options.versions : false,
					includeTrailingDelimiter: options.includeTrailingDelimiter != null ? options.includeTrailingDelimiter : false,
					pageToken               : nextPageToken,
					projection              : options.projection ?: 'noAcl',
					delimiter               : options.delimiter
			]]
			log.debug "listFiles with requestOpts ${requestOpts}"
			def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'GET')
			if(results.success) {
				if(options.delimiter) {
					def prefixes = []
					for(p in results.data.prefixes) {
						if(p != options.prefix) {
							if(options.prefix) {
								prefixes << (options.prefix) + p.substring(options.prefix.length()).split(options.delimiter)[0]
							} else {
								prefixes << p.split(options.delimiter)[0]
							}


						}
					}
					prefixes.unique()
					prefixes.each { prefix ->
						files << new GoogleCloudDirectory(provider: provider, parent: thisDirectory, name: prefix)
					}

					for(f in results.data.items) {
						if(f.name != options.prefix || !options.prefix.endsWith(options.delimiter)) {
							files << new GoogleCloudFile(name: f.name, provider: provider, parent: thisDirectory, existsFlag: true)
						}
					}

				} else if(results.data.items) {
					files += results.data.items?.collect { f -> new GoogleCloudFile([name: "${name}/${f.name}", provider: provider, parent: thisDirectory, existsFlag: true]) }
				}

				nextPageToken = results.data.nextPageToken
				if(!nextPageToken) {
					keepGoing = false
				}
			} else {
				throw new Exception("Error in calling google api: ${googleStorageProvider.parseRestError(results)}")
			}
		}
		files
	}

	GoogleCloudDirectory getDirectory(String fullDirectoryName) {
		def directoryName = fullDirectoryName
		new GoogleCloudDirectory(provider: provider, name: directoryName, parent: this)
	}

	def save() {
		log.debug "save: ${name}, ${location}, ${locationType}, ${storageClass}"
		GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider
		def path = "storage/v1/b"
		def requestOpts = [body: [name: name], query: [project: provider.projectId]]
		if(location) {
			requestOpts.body.location = location
		}
		if(storageClass) {
			requestOpts.body.storageClass = storageClass
		}
		def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'POST')
		if(!results.success && results.statusCode == 409) {
			// try a patch to prove we own it
			results = googleStorageProvider.callApi("https://storage.googleapis.com", "${path}/${name}", requestOpts, 'PATCH')
		}
		if(!results.success) {
			log.error "Error saving bucket with name ${name}: ${results}"
		}
		metaDataLoaded = false
		results.success
	}

	def delete() {
		log.debug "delete: ${name}"
		// To delete a bucket (directory) must delete ALL the objects in it first
		def path = "storage/v1/b/${name}/o"
		def keepGoing = true
		GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider

		def requestOpts = [query: [maxResults : 100, projection :'noAcl']]

		def deleteBucket = false
		while(keepGoing) {
			def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, requestOpts, 'GET')
			log.debug "found ${results.data.items?.size()} objects in ${name} to delete"
			if(results.success) {
				if(results.data.items?.size() > 0) {
					for (f in results.data.items) {
						def deletePath = "storage/v1/b/${name}/o"
						def deleteOpts = [additionalPathSegments: [f.name]]
						googleStorageProvider.callApi("https://storage.googleapis.com", deletePath, deleteOpts, 'DELETE')
					}
				} else {
					keepGoing = false // no more!
					deleteBucket = true
				}
			} else {
				log.error "Error in listing items ${path} ${results}"
				keepGoing = false
			}
		}

		if(deleteBucket) {
			log.debug "Deleting bucket ${name}"
			def deleteResults = googleStorageProvider.callApi("https://storage.googleapis.com", "storage/v1/b/${name}", [:], 'DELETE')
			if(deleteResults.success) {
				return true
			} else {
				log.error "Error in deleting bucket ${deleteResults}"
				return false
			}
		} else {
			return false
		}
	}

	CloudFile getFile(String name) {
		new GoogleCloudFile(provider: provider, parent: this, name: name)
	}

	private void loadObjectMetaData() {
		log.debug "loadObjectMetaData ${name}"

		metaDataLoaded = true

		GoogleStorageProvider googleStorageProvider = (GoogleStorageProvider) provider
		def path = "storage/v1/b/${name}"
		def results = googleStorageProvider.callApi("https://storage.googleapis.com", path, [:], 'GET')
		if(results.success) {
			this.location = results.data.location
			this.locationType = results.data.locationType
			this.storageClass = results.data.storageClass
		}
	}
}