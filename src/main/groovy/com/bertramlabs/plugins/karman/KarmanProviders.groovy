package com.bertramlabs.plugins.karman

import groovy.util.logging.Commons

@Commons
class KarmanProviders {
	static final String FACTORIES_RESOURCE_LOCATION = "META-INF/karman/providers.properties"
    private static Map<String,Class<StorageProviderInterface>> providers = null
    /**
     * Load the storage provider classes and adds them to the provider factory
     *
     * @param classLoader The classloader
     *
     * @return A list of provider classes
     */
    static Map<String,Class<StorageProviderInterface>> loadProviders(ClassLoader classLoader = Thread.currentThread().contextClassLoader) {

        if(providers == null) {
            def resources = classLoader.getResources(FACTORIES_RESOURCE_LOCATION)
            providers = [:]

            resources.each { URL res ->
	        	Properties providerProperties = new Properties()
            	providerProperties.load(res.openStream())

            	providerProperties.keySet.each { providerName ->
            		try {
	            		String className = providerProperties.getProperty(providerName)
	            		def cls = classLoader.loadClass(className)
	            		if(StorageProviderInterface.isAssignableFrom(cls)) {
	            			if(!providers[providerName]) {
	            				providers[providerName] = cls
	            			}
	        			} else {
	            			log.warn("Karman Storage Provider $className not registered because it does not implement the StorageProviderInterface")		
	        			}
	        		} catch(Throwable e) {
	        			log.error("Error Loading Karman Storage Provider $className: $e.message",e)
	        		} 
            	}
	        }
	    }
    	return providers
    }
}