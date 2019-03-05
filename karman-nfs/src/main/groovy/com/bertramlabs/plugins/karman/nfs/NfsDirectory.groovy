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

		def delimiter = options.delimiter
		FileSystem fileSystem = FileSystems.getDefault()
		String prefix
		def excludes = []
		def includes = []
		//setup options
		options.excludes?.each { exclude ->
			excludes << fileSystem.getPathMatcher(exclude)
		}
		options.includes?.each { include ->
			includes << fileSystem.getPathMatcher(include)
		}
		Nfs3File rootFolder = baseFile
		prefix = options.prefix as String

		if(delimiter == '/' && prefix) {
			prefix = normalizePath(prefix)
		}

		if(options.prefix) {
			excludes << fileSystem.getPathMatcher("glob:*")
			excludes << fileSystem.getPathMatcher("glob:**/*")
			if(prefix.endsWith("/")) {
				rootFolder = new Nfs3File(baseFile.nfs,baseFile.path + prefix.substring(0,prefix.length() - 1))
				if(delimiter == '/') {
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
				} else {
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
					includes << fileSystem.getPathMatcher("glob:${prefix}**/*")
				}
			} else {
				if(delimiter == '/') {
					if(prefix.lastIndexOf('/') > 0) {
						rootFolder = new Nfs3File(baseFile.nfs,baseFile.path + prefix.substring(0,prefix.lastIndexOf('/')))
					}
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
					includes << fileSystem.getPathMatcher("glob:${prefix}*/**/*")
				} else {
					includes << fileSystem.getPathMatcher("glob:${prefix}*")
				}
			}
		} else if (options.delimiter) {
			excludes << fileSystem.getPathMatcher("glob:*")
			excludes << fileSystem.getPathMatcher("glob:**/*")
			includes << fileSystem.getPathMatcher("glob:*")
		}
		convertFilesToCloudFiles(rootFolder, includes,excludes, rtn)

		if(delimiter != '/') {
			for(int counter = 0; counter < rtn.size(); counter++) {
				NfsCloudFile currentFile = rtn[counter]
				if(currentFile.isDirectory()) {
					convertFilesToCloudFiles(currentFile.baseFile, includes, excludes, rtn, counter + 1)
				}
			}
		}
		rtn = rtn?.findAll {
			isMatchedFile(it.name,includes,excludes)
		}?.sort{ a, b ->  a.isFile() <=> b.isFile() ?: a.name <=> b.name}

		return rtn
	}

	private void convertFilesToCloudFiles(Nfs3File parentFile, includes, excludes, fileList, position=0) {
		Collection<NfsCloudFile> files = [];
		parentFile.listFiles()?.each { listFile ->
			def path = listFile.path.substring(baseFile.path.length() - 1)
			if(path.startsWith('/')) {
				path = path.substring(1)
			}
			if(isMatchedFile(path,includes,excludes)) {
				files << new NfsCloudFile(provider:provider, parent:this, name:path, baseFile: listFile)
			}
		}
		if(files) {
			fileList.addAll(position, files)
		}
	}

	private Boolean isMatchedFile(String stringPath, includes,excludes) {
		Path path = Paths.get(stringPath)
		Boolean rtn = true
		if(excludes?.size() > 0) {
			excludes?.each { exclude ->
				if(exclude.matches(path)) {
					rtn = false
				}

			}
		}
		if(includes?.size() > 0) {
			includes?.each { include ->
				if(include.matches(path)) {
					rtn = true
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


	def delete() {
		if(baseFile.exists()) {
			baseFile.delete()
		}
	}


	// PRIVATE

	private NfsCloudFile cloudFileFromNfs3File(Nfs3File file) {
		new NfsCloudFile(
			provider: provider,
			parent: this,
			name: file.path,
			baseFile: file
		)
	}
}

