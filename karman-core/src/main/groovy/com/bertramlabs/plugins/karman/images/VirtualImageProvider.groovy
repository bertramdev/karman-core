package com.bertramlabs.plugins.karman.images

import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.exceptions.ProviderNotFoundException

/**
 * This is the base provider abstract class for defining various virtual image providers. This class also provides a factory {@code create()} static method for acquiring
 * an instance of a particular image provider.
 * <p>
 * Below is an example of how a AWSVirtualImageProvider might be initialized.
 * </p>
 * <pre>
 * {@code
 * import com.bertramlabs.plugins.karman.images.VirtualImageProvider
 * def provider = StorageProvider(
 *  provider: 'aws',
 *  accessKey: "mykey",
 *  secretKey: "mysecret",
 *  region: "us-east-1"
 * )
 *
 * //Shorthand
 * provider['ami-3456'].exists()
 * //or
 * provider.'ami-34567'.name = "set name tag"
 * }
 * </pre>
 * @author David Estes
 */
public abstract class VirtualImageProvider implements VirtualImageProviderInterface {

	/**
	 * A Factory method for creating a new virtual image provider of a type. Typically the type is passed as a provider
	 * key which correlates to the classes providerName. These can be registered by creating a Properties file in META-INF/karman/imageProviders.properties with the key
	 * being the provider name and the value being the fully qualified class name
	 * @param options - Takes an array of options and passes to the constructor of the provider. Passing [provider: 'aws'] would fetch an aws Provider.
	 */
	public static synchronized VirtualImageProvider create(options = [:]) {
		def provider = options.remove('provider')
		if(!provider) {
			throw new ProviderNotFoundException()
		}
		def providerClass = KarmanConfigHolder.imageProviderTypes.find{ it.key == provider}?.value
		if(!providerClass) {
			throw new ProviderNotFoundException(provider)
		}
		return providerClass.newInstance(options)
	}

	/**
	 * Registers VirtualImageProviders for various cloud providers into the VirtualImageProvider create factory
	 * @param provider  The class that extends VirtualImageProvider and that gets registered by its provider name.
	 */
	static registerProvider(provider) {
		def providerClass = KarmanConfigHolder.imageProviderTypes.find{ it.key == provider.providerName}?.value

		if(!providerClass) {
			KarmanConfigHolder.providerTypes[provider.providerName] = provider
		}
	}

	/**
	 * Used to map to the getAt helper for Image Names or Ids. Not directly called by a user.
	 */
	def propertyMissing(String propName) {
		getAt(propName)
	}
}
