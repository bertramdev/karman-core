package com.bertramlabs.plugins.karman;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;


class KarmanProviders {
	//get slf4j logger
	private static final Logger log = LoggerFactory.getLogger(KarmanProviders.class);
	static final String FACTORIES_RESOURCE_LOCATION = "META-INF/karman/providers.properties";
	static final String NETWORK_FACTORIES_RESOURCE_LOCATION = "META-INF/karman/networkProviders.properties";
    private static Map<String,Class<StorageProviderInterface>> providers = null;


	static synchronized Map<String,Class<StorageProviderInterface>> loadProviders() throws IOException {
		return loadProviders(Thread.currentThread().getContextClassLoader());
	}

	/**
	 * Load the storage provider classes and adds them to the provider factory
	 *
	 * @param classLoader The classloader
	 *
	 * @return A list of provider classes
	 */
	static synchronized Map<String,Class<StorageProviderInterface>> loadProviders(ClassLoader classLoader) throws IOException {

        if(providers == null) {
            Enumeration<URL> resources = classLoader.getResources(FACTORIES_RESOURCE_LOCATION);
            providers = new LinkedHashMap<>();
			while(URL res = resources.nextElement() != null) {

			}
			for(URL res : resources.asIterator()) {

	        	Properties providerProperties = new Properties();
            	providerProperties.load(res.openStream());

            	providerProperties.keySet().each { providerName ->
            		try {
	            		String className = providerProperties.getProperty(providerName);
	            		def cls = classLoader.loadClass(className);
	            		if(StorageProviderInterface.isAssignableFrom(cls)) {
	            			if(!providers[providerName]) {
	            				providers[providerName] = cls;
	            			}
	        			} else {
	            			log.warn("Karman Storage Provider $className not registered because it does not implement the StorageProviderInterface")		
	        			}
	        		} catch(Throwable e) {
	        			log.error("Error Loading Karman Storage Provider $className: $e.message",e);
	        		} 
            	}
	        }
	    }
    	return providers;
    }



}
