package com.bertramlabs.plugins.karman.aws.images

import com.amazonaws.services.ec2.model.BlockDeviceMapping
import com.bertramlabs.plugins.karman.images.VirtualImageVolumeInterface


/**
 * A representation of a BlockDeviceMapping in Amazon as it relates to an AMI
 * The list of all Volumes can be acquired from a {@link AWSVirtualImage}
 *
 * @author David Estes
 */
class AWSVirtualImageVolume implements VirtualImageVolumeInterface {
	BlockDeviceMapping deviceMapping

	protected Boolean rootVolume = false
	String uid
	Long size
	String deviceName
	String volumeType
	String volumeLabel


	AWSVirtualImageVolume(BlockDeviceMapping deviceMapping) {
		this.deviceMapping = deviceMapping
		deviceName = deviceMapping.deviceName
		uid = deviceMapping.getEbs()?.getSnapshotId()
		size = deviceMapping.ebs?.getVolumeSize()
		volumeType = deviceMapping.ebs?.getVolumeType()
		volumeLabel = deviceMapping.getVirtualName()
	}

	@Override
	Boolean isRootVolume() {
		return this.rootVolume
	}

	void setRootVolume(Boolean rootVolume) {
		this.rootVolume = rootVolume;
	}
}
