package com.bertramlabs.plugins.karman.network

import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.exceptions.ProviderNotFoundException

/**
 * Created by davidestes on 3/1/16.
 */

abstract class NetworkProvider implements NetworkProviderInterface {

	/**
	 * A Factory method for creating a new storage provider of a type. Typically the type is passed as a provider
	 * key which correlates to the classes providerName. These can be registered by creating a Properties file in META-INF/karman/provider.properties with the key
	 * being the provider name and the value being the fully qualified class name
	 * @param options - Takes an array of options and passes to the constructor of the provider. Passing [provider: 'local'] would fetch a local Provider.
	 */
	public static synchronized NetworkProvider create(options = [:]) {
		def provider = options.remove('provider')
		if(!provider) {
			throw new ProviderNotFoundException()
		}
		def providerClass = KarmanConfigHolder.networkProviderTypes.find{ it.key == provider}?.value
		if(!providerClass) {
			throw new ProviderNotFoundException(provider)
		}
		return providerClass.newInstance(options)
	}


	@Override
	public Collection<SecurityGroupInterface> getSecurityGroups() {
		return getSecurityGroups([:])
	}


}
