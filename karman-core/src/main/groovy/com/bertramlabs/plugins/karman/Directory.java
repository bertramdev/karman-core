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

package com.bertramlabs.plugins.karman;

import com.bertramlabs.plugins.karman.util.Mimetypes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

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
public abstract class Directory<F extends CloudFileInterface> implements DirectoryInterface<F> {
	protected StorageProvider provider;

	protected Directory parent;

	protected String name;


	/**
	* Checks if this object is a file or not (Always false).
	* This is in place for compatibility in your code when jumping between files and directories.
	* @return false as a directory is never a file.
	*/
	public Boolean isFile() {
		return false;
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
	public F getAt(String key) {
		return getFile(key);
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
	public void putAt(String key, F file)  {
		file.save();
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
      try {
          putAt(key, Files.readAllBytes(file.toPath()));
      } catch (IOException e) {
          throw new RuntimeException(e);
      }
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
	  F cloudFile = getFile(key);
	  String mimeType = Mimetypes.getInstance().getMimetype(key);
	  if(mimeType != null) {
		  cloudFile.setContentType(mimeType);
	  }
		cloudFile.setBytes(bytes);
		cloudFile.save();
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
		F cloudFile = getFile(key);
		String mimeType = Mimetypes.getInstance().getMimetype(key);
    	if(mimeType != null) {
			cloudFile.setContentType(mimeType);
		}
		cloudFile.setText(text);
		cloudFile.save();
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
	Object propertyMissing(String propName) {
		return getAt(propName);
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
	void propertyMissing(String propName, String value) {
		putAt(propName,value);
	}

	/**
	* Creates or saves this directory to the cloud store if it does not already exist.
	*/
  	public void mkdir() {
		save();
	}

	/**
	* Creates or saves this directory to the cloud store if it does not already exist.
	*/
	public void mkdirs() {
		save();
	}

	/**
	* Checks is this is a directory or not (Always true).
	* @return true
	*/
	public Boolean isDirectory() {
		return true;
	}

	/**
	 * Scaffold for checking content type... For a directory this is typically null
	 * @return null
	 */
	public String getContentType() {
		return null;
	}

	/**
	* Displays the name of the directory when cast to a String.
	* @return name of the directory
	*/
	public String toString() {
		return getName();
  	}

	public static String normalizePath(String path) {
		boolean addSuffixDelimiter = path.endsWith("/");
        String[] pathArgs = path.split("/");
		ArrayList<String> newPath = new ArrayList<String>();
		for (int counter = 0; counter < pathArgs.length; counter++) {
			String pathElement = pathArgs[counter];
			if (pathElement.equals("..")) {
				if (!newPath.isEmpty()) {
					newPath.remove(newPath.size() - 1);
				} else if (counter < pathArgs.length - 1) {
					counter++;
                }
			} else if(!pathElement.equals(".")) {
				newPath.add(pathElement);
			}
		}
		return String.join("/", newPath) + (addSuffixDelimiter ? "/" : "");
	}

	/**
	* Reference to the provider which instantiated this class
	*/
	public StorageProvider getProvider() {
		return provider;
	}

	public void setProvider(StorageProvider provider) {
		this.provider = provider;
	}

	/**
	* Parent Directory (not commonly used)
	*/
	public Directory getParent() {
		return parent;
	}

	public void setParent(Directory parent) {
		this.parent = parent;
	}

	/**
	* Directory / Bucket Name
	*/
	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
