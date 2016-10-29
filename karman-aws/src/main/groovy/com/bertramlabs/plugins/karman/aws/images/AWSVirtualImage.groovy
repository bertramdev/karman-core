package com.bertramlabs.plugins.karman.aws.images

import com.amazonaws.services.ec2.model.ArchitectureValues
import com.amazonaws.services.ec2.model.BlockDeviceMapping
import com.amazonaws.services.ec2.model.DeregisterImageRequest
import com.amazonaws.services.ec2.model.Image
import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.images.VirtualImage
import com.bertramlabs.plugins.karman.images.VirtualImageType
import com.bertramlabs.plugins.karman.util.Architecture
import com.bertramlabs.plugins.karman.util.OperatingSystem
import groovy.transform.CompileStatic

/**
 * Karman representation of an Amazon AMI record. This gets abstracted out into a standard Karman {@link VirtualImage} object
 * The base Amazon Image representation can be acquired via the 'getAmazonImage()' method.
 * @author David Estes
 */
@CompileStatic
public class AWSVirtualImage extends VirtualImage {

	String uid
	String name
	OperatingSystem operatingSystem
	List<AWSVirtualImageVolume> volumes
	Image amazonImage
	AWSVirtualImageProvider provider

	AWSVirtualImage(AWSVirtualImageProvider provider, String name) {
		this.provider = provider
		this.name = name
	}

	AWSVirtualImage(AWSVirtualImageProvider provider, Image image) {
		this.amazonImage = image
		name = image.name
		this.provider = provider
		uid = image.getImageId()
		if(image.platform == 'windows') {
			operatingSystem = OperatingSystem.windowsInstance(image.architecture == ArchitectureValues.X86_64.toString() ? Architecture.X86_64 : Architecture.X86)
		} else {
			operatingSystem = OperatingSystem.linuxInstance(image.architecture == ArchitectureValues.X86_64.toString() ? Architecture.X86_64 : Architecture.X86)
		}



	}

	@Override
	VirtualImageType getImageType() {
		VirtualImageType.AMI
	}

	@Override
	void setImageType(VirtualImageType imageType) {
		//not implemented
		throw new UnsupportedOperationException("Cannot change image type on Amazon as it only supports the AMI image type.")
	}


	@Override
	void setOperatingSystem(OperatingSystem system) {
		// not settable
		throw new UnsupportedOperationException("Cannot change operating system as this is inferred from the Amazon DescribeImage API")
	}

	@Override
	Long getContentLength() {
		Long total = 0
		getVolumes()?.each { AWSVirtualImageVolume volume ->
			total += volume.size ?: 0
		}
		return total
	}

	@Override
	Long getVolumeCount() {
		return getVolumes()?.size() ?: 0
	}

	List<AWSVirtualImageVolume> getVolumes() {
		if(!volumes && amazonImage?.blockDeviceMappings.size() > 0) {
			volumes = amazonImage.blockDeviceMappings.collect{ BlockDeviceMapping mapping ->
				AWSVirtualImageVolume volume = new AWSVirtualImageVolume(mapping)
				if(amazonImage.rootDeviceName == mapping.deviceName) {
					volume.rootVolume = true
				}
				return volume
			}
		}
		return volumes
	}

	@Override
	Boolean isPublic() {
		return amazonImage?.public
	}

	@Override
	void setPublic(Boolean publicImage) {
		if(!amazonImage) {
			amazonImage = new Image();
		}
		amazonImage.setPublic(publicImage)
	}

	@Override
	void setFiles(Collection<CloudFile> cloudFiles) {

	}

	@Override
	Boolean exists() {
		if(amazonImage?.getImageId()) {
			return true
		}
		return false
	}

	@Override
	Boolean save() {
		//TODO: Import AMI Task work?
		return null
	}

	@Override
	Boolean remove() {
		DeregisterImageRequest request = new DeregisterImageRequest(uid)
		provider.EC2Client.deregisterImage(request)
		return true
	}
}
