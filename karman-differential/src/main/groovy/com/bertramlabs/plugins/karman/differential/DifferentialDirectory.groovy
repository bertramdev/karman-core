package com.bertramlabs.plugins.karman.differential

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.CloudFileInterface
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.DirectoryInterface

public class DifferentialDirectory extends Directory{
	DirectoryInterface sourceDirectory

	DifferentialDirectory(String name, DifferentialStorageProvider provider, DirectoryInterface sourceDirectory) {
		this.name = name
		this.provider = provider
		this.sourceDirectory = sourceDirectory
	}

	@Override
	Boolean exists() {
		return sourceDirectory.exists()
	}

	@Override
	List listFiles(options = [:]) {
		List<DifferentialCloudFile> rtn = sourceDirectory.listFiles(options)?.collect { CloudFileInterface file ->
			new DifferentialCloudFile(file.name, this, file)
		}
		return rtn
	}

	@Override
	CloudFile getFile(String name) {
		CloudFileInterface file = sourceDirectory.getFile(name)
		if (file) {
			return new DifferentialCloudFile(name, this, file)
		} else {
			return null
		}
	}

	@Override
	def save() {
		sourceDirectory.save()
	}

	@Override
	def delete() {
		sourceDirectory.delete()
	}
}
