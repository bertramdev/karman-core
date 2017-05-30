package com.bertramlabs.plugins.karman.nfs

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.Directory
import com.emc.ecs.nfsclient.nfs.io.Nfs3File
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3
import com.emc.ecs.nfsclient.rpc.CredentialUnix

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

	@Override
	List listFiles(Object options) {
		return null
	}

	@Override
	CloudFile getFile(String name) {
		return cloudFileFromNfs3File(new Nfs3File(baseFile,name))
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

