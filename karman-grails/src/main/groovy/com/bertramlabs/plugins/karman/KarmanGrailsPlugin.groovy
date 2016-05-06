package com.bertramlabs.plugins.karman

import com.bertramlabs.plugins.karman.local.LocalStorageProvider
import grails.plugins.*

class KarmanGrailsPlugin extends Plugin {
    def version         = "0.7.2"
    def grailsVersion   = "3.0 > *"
    def title           = "Karman Plugin"
    def author          = "David Estes"
    def authorEmail     = "destes@bcap.com"
    def description     = 'Karman is a standardized / extensible interface plugin for dealing with various cloud services including Local and S3.'
    def documentation   = "http://bertramdev.github.io/karman"
    def license         = "APACHE"
    def organization    = [name: "Bertram Labs", url: "http://www.bertramlabs.com/"]
    def issueManagement = [ system: "GITHUB", url: "http://github.com/bertramdev/karman/issues" ]
    def scm             = [ url: "http://github.com/bertramdev/karman" ]
    def pluginExcludes  = [
    ]
    def developers      = [ [name: 'Brian Wheeler'] ]


    def doWithApplicationContext = { applicationContext ->
        def config = grailsApplication.config.grails.plugin.karman

        KarmanConfigHolder.config = config

    }
}
