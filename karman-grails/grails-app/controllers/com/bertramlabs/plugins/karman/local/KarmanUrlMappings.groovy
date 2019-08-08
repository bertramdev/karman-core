package com.bertramlabs.plugins.karman.local

import org.springframework.context.ApplicationContext

class KarmanUrlMappings {

	static mappings = { ApplicationContext context ->

		def config = context.grailsApplication.config.grails.plugin.karman
		def serveLocalStorage = config.containsKey('serveLocalStorage') ? config.serveLocalStorage : true
		def path = config.serveLocalMapping ?: 'storage'

		if(serveLocalStorage) {
			"/$path/$directory/$id**" (
				controller: 'localStorage',
				plugin: 'karmanGrails',
				action: 'show'
			)
			"/$path/$id**" (
				controller: 'localStorage',
				plugin: 'karmanGrails',
				action: 'show'
			)
		}

	}
}
