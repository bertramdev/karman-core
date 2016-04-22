includeTargets << grailsScript("_GrailsInit")


target(initKarman: "Load Karman Config to Configuration Holder") {
    depends(compile, parseArguments)
    def karmanConfigHolder = classLoader.loadClass('com.bertramlabs.plugins.karman.KarmanConfigHolder')
    karmanConfigHolder.config = grailsApp.config.grails.plugin.karman

	event("KarmanConfig", [karmanConfigHolder])

}
