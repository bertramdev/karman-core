package com.bertramlabs.plugins.karman.nfs

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import com.emc.ecs.nfsclient.nfs.io.Nfs3File
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3
import com.emc.ecs.nfsclient.rpc.CredentialUnix
import java.nio.file.*

/**
 * NFS Base directory implementation
 *
 * @author David Estes
 */
class NfsDirectory extends Directory {

	Nfs3File baseFile

	@Override
	Boolean exists() {
		return baseFile.exists()
	}

	List listFiles(options = [:]) {
		def rtn = []
		recurseFiles(baseFile, null, rtn, options)
		return rtn
	}

	List recurseFiles(Nfs3File file, String parentPath, List results, options = [:]) {
		def delimiter = options.delimiter ?: '/'
		FileSystem fileSystem = FileSystems.getDefault()
		def prefix
		def excludes = []
		def includes = []
		//setup options
		options.excludes?.each { exclude ->
			excludes << fileSystem.getPathMatcher(exclude)
		}
		options.includes?.each { include ->
			includes << fileSystem.getPathMatcher(include)
		}
		if(options.prefix)
			prefix = fileSystem.getPathMatcher(options.prefix)
		//iterate files
		def fileList = file?.listFiles()
		fileList?.each { fileRow ->
			def path = parentPath ? fileSystem.getPath(parentPath, fileRow.path) : "/"
			def addFile = true
			if(excludes?.size() > 0) {
				excludes?.each { exclude ->
					if(exclude.matches(path))
						addFile == false
				}
			}
			if(addFile == true && includes?.size() > 0) {
				addFile = false
				includes?.each { include ->
					if(include.matches(path)) {
						addFile == true
					}
				}
			}
			if(addFile == true && prefix) {
				addFile = false
				if(prefix.matches(path))
					addFile = true
			}
			if(addFile == true) {
				results << new NfsCloudFile(provider:provider, parent:this, name:fileRow.path)
				if(fileRow.isDirectory()) {
					def newParent = (parentPath ?: '') + delimiter + file.name
					recurseFiles(fileRow, newParent, results, options)
				}
			}
		}
	}

	@Override
	CloudFile getFile(String name) {

		def path = baseFile.path + '/' + name
		return cloudFileFromNfs3File(new Nfs3File(baseFile.nfs,path))
	}

	@Override
	def save() {
		if(!baseFile.exists()) {
			baseFile.mkdirs()
		}
		return null
	}


	// PRIVATE

	private NfsCloudFile cloudFileFromNfs3File(Nfs3File file) {
		new NfsCloudFile(
			provider: provider,
			parent: this,
			name: file.name,
			baseFile: file
		)
	}
}

