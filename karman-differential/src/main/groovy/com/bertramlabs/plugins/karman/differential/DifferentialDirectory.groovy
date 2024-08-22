package com.bertramlabs.plugins.karman.differential

import com.bertramlabs.plugins.karman.CloudFileInterface
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.DirectoryInterface

public class DifferentialDirectory extends Directory<DifferentialCloudFile>{
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
	List<DifferentialCloudFile> listFiles(Map<String, Object> options) {
		List<DifferentialCloudFile> rtn = sourceDirectory.listFiles(options)?.collect { CloudFileInterface file ->
			new DifferentialCloudFile(file.name, this, file)
		}
		return rtn
	}

	@Override
	DifferentialCloudFile getFile(String name) {
		CloudFileInterface file = sourceDirectory.getFile(name)
		if (file) {
			return new DifferentialCloudFile(name, this, file)
		} else {
			return null
		}
	}

	@Override
	void save() {
		sourceDirectory.save()
	}

	@Override
	void delete() {
		sourceDirectory.delete()
	}
}
