package com.bertramlabs.plugins.karman.differential

import com.bertramlabs.plugins.karman.DirectoryInterface
import com.bertramlabs.plugins.karman.StorageProvider

class DifferentialStorageProvider extends StorageProvider<DifferentialDirectory> {

	static String providerName = "differential"

	Integer blockSize = 1024 * 1024 * 1 //1 megabytes per block

	StorageProvider sourceProvider

	/**
	 * Get a list of directories within the storage provider (i.e. Buckets/Containers)
	 * @return List of {@link com.bertramlabs.plugins.karman.Directory} Classes.
	 */
	@Override
	DifferentialDirectory getDirectory(String name) {
		DirectoryInterface dir = sourceProvider.getDirectory(name)
		if(dir) {
			return new DifferentialDirectory(name, this, dir)
		} else {
			return null
		}

	}

	@Override
	List<DifferentialDirectory> getDirectories() {
		return sourceProvider.getDirectories()?.collect { DirectoryInterface dir ->
			new DifferentialDirectory(dir.name, this, dir)
		}
	}
}