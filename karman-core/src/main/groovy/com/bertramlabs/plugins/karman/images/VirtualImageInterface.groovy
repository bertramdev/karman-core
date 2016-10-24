package com.bertramlabs.plugins.karman.images

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.util.OperatingSystem

/**
 * This interface provides a representation of a {@link VirtualImage} object. All Virtual Images must implement these base methodsa
 */
interface VirtualImageInterface {

	public String getUid()
	public String getName()
	public void setName(String name)

	public VirtualImageType getImageType()
	public void setImageType(VirtualImageType imageType)

	public OperatingSystem getOperatingSystem()
	public void setOperatingSystem(OperatingSystem system)

	public Long getContentLength()
	public Long getVolumeCount()
	public Collection<VirtualImageVolumeInterface> getVolumes()

	public Boolean isPublic()
	public void setPublic(Boolean publicImage)


	//Need method to set files related to the virtual image
	public void setFiles(Collection<CloudFile> cloudFiles)

	public Boolean exists()
	public Boolean save()
	public Boolean remove()

}