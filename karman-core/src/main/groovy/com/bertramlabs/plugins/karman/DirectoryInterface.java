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

import java.util.List;
import java.util.Map;

/**
* An Interface for interacting with Cloud Containers (i.e. Buckets). If looking to add new cloud providers,
* one must implement this interface via the {@link com.bertramlabs.plugins.karman.Directory} abstract class.
* @author David Estes
*/
public interface DirectoryInterface<F extends CloudFileInterface> {

	String getName();

	Boolean exists();

	Boolean isFile();

	Boolean isDirectory();

	/**
	 * Scaffold for checking content type... For a directory this is typically null
	 */
	String getContentType();

	List<F> listFiles(Map<String,Object> options);

	F getFile(String name);

	public F getAt(String key);

	void save();

	void delete();

}
