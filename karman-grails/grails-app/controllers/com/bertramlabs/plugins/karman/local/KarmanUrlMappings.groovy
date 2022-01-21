package com.bertramlabs.plugins.karman.local

import org.springframework.context.ApplicationContext

class KarmanUrlMappings {

	static mappings = { ApplicationContext context ->

		def config = context.grailsApplication.config.grails.plugin.karman
		def serveLocalStorage = context.grailsApplication.config.getProperty('grails.plugin.karman.serveLocalStorage',Boolean,true)
		def path = context.grailsApplication.config.getProperty('grails.plugin.karman.serveLocalMapping',String,'storage')

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
