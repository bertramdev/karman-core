/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bertramlabs.plugins.karman

import com.bertramlabs.plugins.karman.exceptions.ProviderNotFoundException


/**
* This is the base provider abstract class for defining various providers. This class also provides a factory {@code create()} static method for acquiring
* an instance of a particular provider.
* <p>
* Below is an example of how a {@link com.bertramlabs.plugins.karman.local.LocalStorageProvider} might be initialized.
* </p>
* <pre>
* {@code
* import com.bertramlabs.plugins.karman.StorageProvider
* def provider = StorageProvider(
*  provider: 'local',
*  basePath: "/path/to/storage/location"
* )
*
* //Shorthand
* provider['folder']['example.txt'] = "This is a string I am storing."
* //or
* provider.'folder'.'example.txt' = "This is a string I am storing."
* }
* </pre>
* @author David Estes
*/
public abstract class StorageProvider implements StorageProviderInterface {

	CloudFileACL defaultFileACL


	/**
	* Convenience method for fetching a Directory/Container By Name.
	* @see {@link com.bertramlabs.plugins.karman.StorageProvider#getDirectory()}
	*/
	public Directory getAt(String key) {
		getDirectory(key)
	}


	/**
	* Get a list of directories within the storage provider (i.e. Buckets/Containers)
	* @return List of {@link com.bertramlabs.plugins.karman.Directory} Classes.
	*/
	abstract def getDirectories()


	/**
	* Gets the Default CloudFile Access Control settings.
	* The Default CloduFileACL can come from either an instance of the StorageProvider or will be pulled from the
	* {@link com.bertramlabs.plugins.karman.KarmanConfigHolder}
	*/
	public CloudFileACL getDefaultFileACL() {
		if(!defaultFileACL) {
			return KarmanConfigHolder.config.defaultFileACL
		}
		return defaultFileACL
	}


	/**
	* A Factory method for creating a new storage provider of a type. Typically the type is passed as a provider
	* key which correlates to the classes providerName. These can be registered by creating a Properties file in META-INF/karman/provider.properties with the key
	* being the provider name and the value being the fully qualified class name
	* @param options - Takes an array of options and passes to the constructor of the provider. Passing [provider: 'local'] would fetch a local Provider.
	*/
	public static synchronized StorageProvider create(options = [:]) {
		def provider = options.remove('provider')
		if(!provider) {
			throw new ProviderNotFoundException()
		}
		def providerClass = KarmanConfigHolder.providerTypes.find{ it.key == provider}?.value
		if(!providerClass) {
			throw new ProviderNotFoundException(provider)
		}
		return providerClass.newInstance(options)
	}

	/**
	* Registers StorageProviders for various cloud providers into the StorageProvider create factory
	* @param provider  The class that extends StorageProvider and that gets registered by its provider name.
	*/
	static registerProvider(provider) {
		def providerClass = KarmanConfigHolder.providerTypes.find{ it.key == provider.providerName}?.value

		if(!providerClass) {
			KarmanConfigHolder.providerTypes[provider.providerName] = provider
		}
	}


	/**
	* Used to map to the getAt helper for Directory Names. Not directly called by a user.
	*/
	def propertyMissing(String propName) {
		getAt(propName)
	}



}
