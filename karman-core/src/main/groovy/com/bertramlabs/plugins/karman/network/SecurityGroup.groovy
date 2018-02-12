package com.bertramlabs.plugins.karman.network

import java.security.MessageDigest

/**
 * Created by davidestes on 3/1/16.
 */
abstract class SecurityGroup implements SecurityGroupInterface{
	String getMd5Hash() {
		def map = [name: this.name, id: this.id, rules:[]]
		this.getRules().each { SecurityGroupRuleInterface rule ->
			map.rules << [targetGroupName: rule.targetGroupName,targetGroupId: rule.targetGroupId, id: rule.id, policy: rule.policy, direction: rule.direction, minPort: rule.minPort, maxPort: rule.maxPort, etherType: rule.ethertype, ipProtocol: rule.ipProtocol, ipRanges: rule.ipRange]
		}

		MessageDigest md = MessageDigest.getInstance("MD5")
		md.update(map.toString().bytes)
		byte[] checksum = md.digest()
		return checksum.encodeHex().toString()
	}
}
