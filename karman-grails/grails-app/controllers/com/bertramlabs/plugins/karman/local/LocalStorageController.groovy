package com.bertramlabs.plugins.karman.local

class LocalStorageController {

    def show() {
    	def config      = grailsApplication.config.grails.plugin.karman
    	def storagePath = config.storagePath ?: 'storage'

        if(!storagePath) {
            log.error("Karman Local Storage Path Not Specified. Please specify in your Config.groovy property: grails.plugins.karman.local.storagePath")
            render status: 500
            return
        }

		def provider    = new LocalStorageProvider(basePath: storagePath)
		def extension   = extensionFromURI(request.requestURI)
		def directoryName = params.directory ?: '.'
		def fileName = params.id
        def format = servletContext.getMimeType(fileName)

        if(extension && !fileName.endsWith(".${extension}")) {
        	fileName = fileName + ".${extension}"
        }

        // No reverse traversal!
        if(request.requestURI.contains('../') || request.requestURI.contains('..\\')) {
        	render status: 402
        	return
        }
        def localFile = provider[directoryName][fileName]
        if(!localFile.exists()) {
            render status: 404
            return
        }

        // TODO: Private File Restrictions

        response.characterEncoding = request.characterEncoding
        response.contentType = format

        if(config.local.sendFileHeader) {
            response.setHeader(config.local.sendFileHeader, localFile.fsFile.canonicalPath)
        } else {
            response.outputStream << localFile.bytes
            response.flushBuffer()
        }

    }

    private extensionFromURI(uri) {

        def uriComponents = uri.split("/")
        def lastUriComponent = uriComponents[uriComponents.length - 1]
        def extension
        if(lastUriComponent.lastIndexOf(".") >= 0) {
            extension = uri.substring(uri.lastIndexOf(".") + 1)
        }
        return extension
    }
}
