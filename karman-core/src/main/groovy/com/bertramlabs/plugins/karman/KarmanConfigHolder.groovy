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

import com.bertramlabs.plugins.karman.images.VirtualImageProviderInterface
import com.bertramlabs.plugins.karman.local.LocalStorageProvider
import com.bertramlabs.plugins.karman.network.NetworkProviderInterface

/**
* Static class for holding a global Configuration Map
* @author David Estes
*/
class KarmanConfigHolder {

	/**
	* Map of configuration options and values that can be used throughout Karman
	*/
	static config = [
		defaultFileACL: CloudFileACL.Private
	]

	/**
	* Map of available provider types registered with the StorageProvider factory
	*/
	//static providerTypes = [local: LocalStorageProvider]
	static Map<String,Class<StorageProviderInterface>> providerTypes = KarmanProviders.loadProviders()

	/**
	 * Map of available network providers registered with the network provider factory
	 */
	static Map<String,Class<NetworkProviderInterface>> networkProviderTypes = KarmanProviders.loadNetworkProviders()

	/**
	 * Map of available image providers registered with the network provider factory
	 */
	static Map<String,Class<VirtualImageProviderInterface>> imageProviderTypes = KarmanProviders.loadImageProviders()

	/**
	* Merges a Map of config properties into the global Config Map
	* @param configMap properties that should be applied to the global configuration.
	*/
	static void setConfig(configMap) {
		config += configMap

		if(configMap.providerTypes) {
			KarmanConfigHolder.providerTypes += configMap.providerTypes
		}
		if(configMap.networkProviderTypes) {
			KarmanConfigHolder.networkProviderTypes += configMap.networkProviderTypes
		}

		if(configMap.imageProviderTypes) {
			KarmanConfigHolder.imageProviderTypes += configMap.imageProviderTypes
		}
	}
}
