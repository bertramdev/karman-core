package com.bertramlabs.plugins.karman

import com.bertramlabs.plugins.karman.local.LocalStorageProvider

class KarmanConfigHolder {
	static config = [
		defaultFileACL: CloudFileACL.Private
	]

	static providerTypes = [local: LocalStorageProvider]
	
	static grailsApplication

	static void setConfig(configMap) {
		config += configMap

		if(configMap.providerTypes) {
			KarmanConfigHolder.providerTypes += configMap.providerTypes
		}
	}
}