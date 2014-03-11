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

import com.bertramlabs.plugins.karman.util.Mimetypes

/** Provides a standardized interface for dealing with files stored in the cloud.
*/
abstract class Directory implements DirectoryInterface {
	StorageProvider provider
	Directory parent
	String name

	Boolean isFile() {
		return false
	}

	public CloudFile getAt(String key) {
		getFile(key)
	}

	public void putAt(String key, CloudFile file)  {
		file.save()
	}

    public void putAt(String key, File file)  {
        putAt(key, file.bytes)
    }

    public void putAt(String key, byte[] bytes)  {
		def cloudFile = getFile(key)	
        mimeType = Mimetypes.instance.getMimetype(key)
        
		if(mimeType) {
			cloudFile.contentType = mimeType
		}
		cloudFile.bytes = bytes
		cloudFile.save()
	}

	public void putAt(String key, String text)  {
		def cloudFile = getFile(key)
		
        mimeType = Mimetypes.instance.getMimetype(key)
        
        if(mimeType) {
			cloudFile.contentType = mimeType
		}
		cloudFile.text = text
		cloudFile.save()
	}

    def mkdir() {
		save()
	}

	def mkdirs() {
		save()
	}

	Boolean isDirectory() {
		return true
	}

	String toString() {
		return name
    }

}