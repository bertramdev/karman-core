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

/** 
* This is an abstract class implementation for managing directories / buckets in the cloud.
* <p>
* Typically you would not use this class directly but rather use an implementation of this class. (i.e. {@link com.bertramlabs.plugins.karman.local.LocalDirectory})
* </p>
* <p>
* Example Usage:
* </p>
* <pre>
* Directory dir = new Directory(provider: provider, name: name)
*
* if(!dir.exists()) {
*     dir.mkdir()    
* }
* //List all files recursively
* dir.listFiles()
*
* //List Files in a sub"folder"
* dir.listFiles(prefix: 'config/')
* </pre>
* @author David Estes
*/
abstract class Directory implements DirectoryInterface {
	/**
	* Reference to the provider which instantiated this class
	*/
	StorageProvider provider

	/**
	* Parent Directory (not commonly used)
	*/
	Directory parent

	/**
	* Directory / Bucket Name
	*/
	String name


	/**
	* Checks if this object is a file or not (Always false).
	* This is in place for compatibility in your code when jumping between files and directories.
	* @return false as a directory is never a file.
	*/
	Boolean isFile() {
		return false
	}

	/**
	* Not directly Used. Enables DSL for getting values of files.
	* <p>
	* Example use of Directory DSL
	* </p>
	* <pre>
	* {@code
	* def file = directory["filename.txt"]
	* }
	* </pre>
	*/
	public CloudFile getAt(String key) {
		getFile(key)
	}

	/**
	* Not directly Used. Enables DSL for setting values of files.
	* <p>
	* Example use of Directory DSL
	* </p>
	* <pre>
	* {@code
	* def originalFile = directory['original.txt']
	* //Copy file to other location
	* directory['newfile.txt'] = originalFile
	* }
	* </pre>
	*/
	public void putAt(String key, CloudFile file)  {
		def cloudFile = getFile(key)	
        def mimeType = Mimetypes.instance.getMimetype(key)
        
		if(mimeType) {
			cloudFile.contentType = mimeType
		}
		cloudFile.setContentLength(file.getContentLength())
		cloudFile.setInputStream(file.getInputStream())
		cloudFile.save()
	}


	/**
	* Not directly Used. Enables DSL for setting values of files.
	* <p>
	* Example use of Directory DSL
	* </p>
	* <pre>
	* {@code
	* //Copy file to other location
	* directory['newfile.txt'] = new File("/path/to/filesystem/file.txt")
	* }
	* </pre>
	*/
    public void putAt(String key, File file)  {
		def cloudFile = getFile(key)	
        def mimeType = Mimetypes.instance.getMimetype(key)
        
		if(mimeType) {
			cloudFile.contentType = mimeType
		}
		cloudFile.setContentLength(file.length())
		cloudFile.setInputStream(file.newInputStream())
		cloudFile.save()
    }

    /**
	* Not directly Used. Enables DSL for setting values of files.
	* <p>
	* Example use of Directory DSL
	* </p>
	* <pre>
	* {@code
	* //Copy bytes to other location
	* byte[] bytes = file.bytes
	* directory['newfile.txt'] = bytes
	* }
	* </pre>
	*/
    public void putAt(String key, byte[] bytes)  {
		def cloudFile = getFile(key)	
        def mimeType = Mimetypes.instance.getMimetype(key)
        
		if(mimeType) {
			cloudFile.contentType = mimeType
		}
		cloudFile.bytes = bytes
		cloudFile.save()
	}

    /**
	* Not directly Used. Enables DSL for setting values of files.
	* <p>
	* Example use of Directory DSL
	* </p>
	* <pre>
	* {@code
	* directory['newfile.txt'] = "Setting string value to file"
	* }
	* </pre>
	*/
	public void putAt(String key, String text)  {
		def cloudFile = getFile(key)
		
        def mimeType = Mimetypes.instance.getMimetype(key)
        
        if(mimeType) {
			cloudFile.contentType = mimeType
		}
		cloudFile.text = text
		cloudFile.save()
	}

	/**
	* Not directly Used. Enables DSL for getting values of files.
	* <p>
	* Example use of Directory DSL
	* </p>
	* <pre>
	* {@code
	* def file = directory."filename.txt"
	* }
	* </pre>
	*/
	def propertyMissing(String propName) {
		getAt(propName)
	}

	/**
	* Not directly Used. Enables DSL for setting values to files.
	* <p>
	* Example use of Directory DSL
	* </p>
	* <pre>
	* {@code
	* directory."filename.txt" = "Contents to put into file"
	* }
	* </pre>
	*/
	def propertyMissing(String propName, value) {
		putAt(propName,value)
	}


	/**
	* Creates or saves this directory to the cloud store if it does not already exist.
	*/
    def mkdir() {
		save()
	}

	/**
	* Creates or saves this directory to the cloud store if it does not already exist.
	*/
	def mkdirs() {
		save()
	}

	/**
	* Checks is this is a directory or not (Always true).
	* @return true
	*/
	Boolean isDirectory() {
		return true
	}

	/**
	* Displays the name of the directory when cast to a String.
	* @return name of the directory
	*/
	String toString() {
		return name
    }

}