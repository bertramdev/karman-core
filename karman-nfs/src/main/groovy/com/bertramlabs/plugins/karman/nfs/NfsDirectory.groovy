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
		Collection<NfsCloudFile> rtn = []

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
		prefix = options.prefix

		convertFilesToCloudFiles(baseFile, includes,excludes, prefix, rtn)

		for(int counter=0;counter < rtn.size(); counter++) {
			NfsCloudFile currentFile = rtn[counter]
			if(currentFile.isDirectory()) {
				convertFilesToCloudFiles(currentFile.baseFile, includes,excludes,prefix, rtn, counter+1)
			}
		}
		if(prefix) {
			rtn = rtn.findAll { file ->
				if(file.name.length() >= prefix.length()) {
					if(file.name.take(prefix.length()) == prefix) {
						return true
					}
				}
				return false
			}
		}

		return rtn
	}

	private void convertFilesToCloudFiles(Nfs3File parentFile, includes, excludes,prefix, fileList, position=0) {
		Collection<NfsCloudFile> files = [];
		parentFile.listFiles()?.each { listFile ->
			def path = listFile.path.substring(baseFile.path.length())
			if(path.startsWith('/')) {
				path = path.substring(1)
			}
			if(isMatchedFile(path,includes,excludes,prefix)) {
				files << new NfsCloudFile(provider:provider, parent:this, name:path, baseFile: listFile)
			}
		}
		if(files) {
			fileList.addAll(position, files)
		}
	}

	private Boolean isMatchedFile(path, includes,excludes,prefix) {
		Boolean rtn = true
		if(excludes?.size() > 0) {
			excludes?.each { exclude ->
				if(exclude.matches(path))
					rtn = false
			}
		}
		if(includes?.size() > 0) {
			includes?.each { include ->
				if(include.matches(path)) {
					rtn = true
				}
			}
		}
		if(prefix) {
			if(path.length() >= prefix.length()) {
				if(path.take(prefix.length()) != prefix) {
					rtn = false
				}
			} else if(path.length() < prefix.length()) {
				if(prefix.take(path.length()) != path) {
					rtn = false
				}
			}
		}
		return rtn
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

