package com.bertramlabs.plugins.karman.images

// import com.bertramlabs.plugins.karman.KarmanConfigHolder
import spock.lang.Specification

class AWSVirtualImageProviderSpec extends Specification {

	def "virtual image provider creates"() {
		when:
		VirtualImageProvider  imageProvider = VirtualImageProvider.create(
			provider:'aws',
			accessKey:System.getProperty('aws.accessKey'),
			secretKey:System.getProperty('aws.secretKey'),
			region:System.getProperty('aws.region')
		)

		then:
		imageProvider
	}
}