package com.bertramlabs.plugins.karman.images

import spock.lang.Shared

// import com.bertramlabs.plugins.karman.KarmanConfigHolder
import spock.lang.Specification

class AWSListVirtualImagesSpec extends Specification {

	@Shared
	VirtualImageProvider imageProvider

	def setup() {
		imageProvider = VirtualImageProvider.create(
			provider:'aws',
			accessKey:System.getProperty('aws.accessKey'),
			secretKey:System.getProperty('aws.secretKey'),
			region:System.getProperty('aws.region')
		)
	}


	void "lists private AMIs from amazon"() {
		when:
		def virtualImages = imageProvider.getVirtualImages(public:false)
//		virtualImages.collect { vm ->
//			println "${vm.toString()} - Volumes: ${vm.getVolumeCount()}"
//			vm.volumes.each {vol ->
//				println "    - ${vol.deviceName} - ${vol.isRootVolume()} - ${vol.uid} - ${vol.getSize()}"
//			}
//		}
		then:
		virtualImages.size() > 0
		virtualImages.find{ img -> img.public == true} == null
	}

	void "lists public AMIs from amazon"() {
		when:
		def virtualImages = imageProvider.getVirtualImages(public:true)
//		virtualImages.collect { vm ->
//			println "${vm.toString()} - Volumes: ${vm.getVolumeCount()}"
//			vm.volumes.each {vol ->
//				println "    - ${vol.deviceName} - ${vol.isRootVolume()} - ${vol.uid} - ${vol.getSize()}"
//			}
//		}
		then:
		virtualImages.size() > 0
		virtualImages.find{ img -> img.public == true} != null
	}

}