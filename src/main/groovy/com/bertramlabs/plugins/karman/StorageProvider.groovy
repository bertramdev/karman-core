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

import com.bertramlabs.plugins.karman.exceptions.ProviderNotFoundException

abstract class StorageProvider implements StorageProviderInterface {
	static String name = "Unimplemented"

	CloudFileACL defaultFileACL

	public Directory getAt(String key) {
		getDirectory(key)
	}

	public CloudFileACL getDefaultFileACL() {
		if(!defaultFileACL) {
			return KarmanConfigHolder.config.defaultFileACL
		}
		return defaultFileACL
	}

	public static StorageProvider create(options = [:]) {
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
}