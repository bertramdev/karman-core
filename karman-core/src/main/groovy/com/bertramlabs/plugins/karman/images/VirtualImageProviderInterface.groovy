package com.bertramlabs.plugins.karman.images

/**
 * Core interface for all {@link VirtualImageProvider} classes.
 * Implementing classes provide methods for querying , downloading, and uploading virtual images to various cloud implementations.
 * @author David Estes
 */
interface VirtualImageProviderInterface {

	public String getProviderName()

	public Collection<VirtualImageInterface> getVirtualImages(Map options)
	public Collection<VirtualImageInterface> getVirtualImages()

	public VirtualImageInterface getVirtualImage(String uid)

	public VirtualImageInterface createVirtualImage(String name)
}