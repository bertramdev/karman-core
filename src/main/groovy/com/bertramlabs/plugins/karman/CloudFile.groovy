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

package com.bertramlabs.plugins.karman

/** 
* Provides a standardized interface for dealing with files stored in the cloud.
* Several methods exist on the CloudFile object to provide similar interactions as one 
* might expect when using a standard java File class.
* <p>
* Below is an example of how a CloudFile might be used. Typically you would not use this class directly but rather use an implementation of this class. (i.e. {@link com.bertramlabs.plugins.karman.local.LocalFile})
* </p>
* <pre>
* CloudFile file = new CloudFile(provider: provider, parent: directory , name: name)
* 
* file.text = "Setting test Content"
* file.contentType = "text/plain"
* if(!file.exists()) {
*     file.save()
* }
* </pre>
* @author David Estes
*/
abstract class CloudFile implements CloudFileInterface {
	StorageProvider provider

	/**
	* Property for setting the file name or retrieving.
	*/
	String name

	Boolean isFile() {
		return true
	}

	CloudFile text(String content) {
		setText(content)
		return this
	}

	CloudFile contentType(String content) {
		setContentType(content)
		return this
	}

	Boolean isDirectory() {
		return false
	}

	String toString() {
		return name
	}


	def save() {
		save(provider.defaultFileACL)
	}
}