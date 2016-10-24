package com.bertramlabs.plugins.karman.images

/**
 * A representation of a Volume associated with a {@link VirtualImage} as they can potentially be connected to multiple volumes
 */
public interface VirtualImageVolumeInterface {
	public Long getSize()
	public String getDeviceName()
	public String getVolumeLabel()
	public String getUid()
	public Boolean isRootVolume()
	public String getVolumeType()
	public void setVolumeType(String volumeType)
}