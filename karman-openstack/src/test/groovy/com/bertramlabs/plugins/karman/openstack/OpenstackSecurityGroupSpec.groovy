package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.network.SecurityGroup
import com.bertramlabs.plugins.karman.network.SecurityGroupRule
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.openstack.OpenstackNetworkProvider
import com.bertramlabs.plugins.karman.openstack.OpenstackSecurityGroupRule
import spock.lang.Specification
import spock.lang.*

class OpenstackSecurityGroupSpec extends Specification {

	@Shared	
	OpenstackNetworkProvider networkProvider

	def setup() {
		this.networkProvider = createNetworkProvider()
	}

	def "get security groups"() {
		when:
		def securityGroups = networkProvider.getSecurityGroups()

		then:
		securityGroups.size() > 0
	}

	def "get security group by name"() {
		setup:
		def securityGroups = networkProvider.getSecurityGroups()
		def defaultId
		securityGroups.each{ sg ->
			if(sg.getName() == 'default') {
				defaultId = sg.getId()
			}
		}

		when:
		SecurityGroupInterface securityGroup = networkProvider.getSecurityGroup(defaultId)

		then:
		securityGroups != null
		securityGroup.getId() == defaultId
		securityGroup.getName() == 'default'
		securityGroup.getRules().size() > 0
	}

	def "create a security group"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test1')
		securityGroup.setDescription('test description')
		
		when:
		securityGroup.save()

		then:
		securityGroup.getId() != null
		securityGroup.getName() == 'test1'
		securityGroup.getDescription() == 'test description'
		securityGroup.getRules().size() == 0

		cleanup:
		securityGroup.delete()
	}

	def "delete a security group"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test2')
		securityGroup.save()
		def id = securityGroup.getId()

		when:
		securityGroup.delete()
		networkProvider.getSecurityGroup(id) 
		
		then:
		thrown RuntimeException
	}

	def "update a security group"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test1')
		securityGroup.setDescription('test description')
		securityGroup.save()
		securityGroup.setName('updated name')

		when:
		securityGroup.save()
		SecurityGroupInterface securityGroupUpdate = networkProvider.getSecurityGroup(securityGroup.getId())

		then:
		securityGroupUpdate.getName() == 'updated name'

		cleanup:
		securityGroup.delete()
	}

	def "create a rule to a security group"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test1')
		SecurityGroupRule rule = securityGroup.createRule()
		rule.addIpRange("50.10.10.10/32")
		rule.setMinPort(23)
		rule.setMaxPort(24)
		rule.setIpProtocol('tcp')
		
		when:
		securityGroup.save()
		securityGroup = networkProvider.getSecurityGroup(securityGroup.getId())

		then:
		securityGroup.getName() == 'test1'
		securityGroup.getRules().size() == 1
		def refetchRule = securityGroup.getRules().first()
		refetchRule?.getId() != null
		refetchRule?.getMinPort() == 23
		refetchRule?.getIpRange().first() == "50.10.10.10/32"
		refetchRule?.getMaxPort() == 24
		refetchRule?.getIpProtocol() == "tcp"

		cleanup:
		securityGroup.delete()
	}

	def "add rule to an existing security group"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test1')
		SecurityGroupRule rule = securityGroup.createRule()
		rule.addIpRange("50.10.10.10/32")
		rule.setMinPort(23)
		rule.setMaxPort(24)
		rule.setIpProtocol('tcp')
		securityGroup.save()
		securityGroup = networkProvider.getSecurityGroup(securityGroup.getId())
		SecurityGroupRule anotherRule = securityGroup.createRule()
		anotherRule.addIpRange("50.10.10.11/32")
		anotherRule.setMinPort(1)
		anotherRule.setMaxPort(65535)
		anotherRule.setIpProtocol('tcp')
		
		when:
		securityGroup.save()
		securityGroup = networkProvider.getSecurityGroup(securityGroup.getId())
		
		then:
		securityGroup.getRules().size() == 2
		def testRule = securityGroup.getRules().find { it.getMinPort() == 1 }
		testRule != null
		testRule?.getId() != null
		testRule?.getMinPort() == 1
		testRule?.getIpRange().first() == "50.10.10.11/32"
		testRule?.getMaxPort() == 65535
		testRule?.getIpProtocol() == "tcp"

		cleanup:
		securityGroup.delete()
	}

	def "update an existing rule in a security group"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test1')
		SecurityGroupRule rule = securityGroup.createRule()
		rule.addIpRange("50.10.10.10/32")
		rule.setMinPort(23)
		rule.setMaxPort(24)
		rule.setIpProtocol('tcp')
		securityGroup.save()
		securityGroup = networkProvider.getSecurityGroup(securityGroup.getId())
		rule = securityGroup.getRules().first()
		rule.addIpRange("50.10.10.11/32")
		rule.setMinPort(24)
		rule.setMaxPort(25)
		rule.setIpProtocol('udp')

		when:
		securityGroup.save()
		def refetchSecurityGroup = networkProvider.getSecurityGroup(securityGroup.getId())

		then:
		refetchSecurityGroup.getName() == 'test1'
		refetchSecurityGroup.getRules().size() == 1
		def refetchRule = refetchSecurityGroup.getRules().first()
		refetchRule?.getId() != null
		refetchRule?.getMinPort() == 24
		refetchRule?.getIpRange().first() == "50.10.10.11/32"
		refetchRule?.getMaxPort() == 25
		refetchRule?.getIpProtocol() == "udp"

		cleanup:
		securityGroup.delete()
	}

	def "delete an existing rule by using removeRule on a security group"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test1')
		SecurityGroupRule rule = securityGroup.createRule()
		rule.addIpRange("50.10.10.10/32")
		rule.setMinPort(23)
		rule.setMaxPort(24)
		rule.setIpProtocol('tcp')
		securityGroup.save()

		securityGroup = networkProvider.getSecurityGroup(securityGroup.getId())
		rule = securityGroup.getRules().first()

		when:
		securityGroup.removeRule(rule)
		securityGroup.save()
		def refetchSecurityGroup = networkProvider.getSecurityGroup(securityGroup.getId())

		then:
		refetchSecurityGroup.getRules().size() == 0
		
		cleanup:
		securityGroup.delete()
	}

	def "delete all rules by using clearRules on a security group"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test1')
		SecurityGroupRule rule = securityGroup.createRule()
		rule.addIpRange("50.10.10.10/32")
		rule.setMinPort(23)
		rule.setMaxPort(24)
		rule.setIpProtocol('tcp')
		securityGroup.save()

		securityGroup = networkProvider.getSecurityGroup(securityGroup.getId())
		rule = securityGroup.clearRules()

		when:
		securityGroup.save()
		def refetchSecurityGroup = networkProvider.getSecurityGroup(securityGroup.getId())

		then:
		refetchSecurityGroup.getRules().size() == 0
		
		cleanup:
		securityGroup.delete()
	}

	def "delete on SecurityGroup rule"() {
		setup:
		SecurityGroup securityGroup = networkProvider.createSecurityGroup('test1')
		SecurityGroupRule rule = securityGroup.createRule()
		rule.addIpRange("50.10.10.10/32")
		rule.setMinPort(23)
		rule.setMaxPort(24)
		rule.setIpProtocol('tcp')
		securityGroup.save()

		securityGroup = networkProvider.getSecurityGroup(securityGroup.getId())
		rule = securityGroup.getRules().first()

		when:
		rule.delete()
		def refetchedSecurityGroup = networkProvider.getSecurityGroup(securityGroup.getId())
		
		then:
		securityGroup.getRules().size() == 0
		refetchedSecurityGroup.getRules().size() == 0
		
		cleanup:
		securityGroup.delete()
	}

	def "add an ipRange via string to a rule"() {
		setup:
		SecurityGroupRule rule = new OpenstackSecurityGroupRule(null, null, null)
		
		when:
		rule.addIpRange("50.10.10.10/32")

		then:
		rule.getIpRange().first() == "50.10.10.10/32"
	}

	def "add an ipRange via List to a rule"() {
		setup:
		SecurityGroupRule rule = new OpenstackSecurityGroupRule(null, null, null)
		def ipranges = new ArrayList<String>()
		ipranges << "1"
		ipranges << "2"

		when:
		rule.addIpRange(ipranges)

		then:
		rule.getIpRange().size() == 1
		rule.getIpRange().first() == "1"
	}

	def "remove an ipRange via String from a rule"() {
		setup:
		SecurityGroupRule rule = new OpenstackSecurityGroupRule(null, null, null)
		rule.addIpRange("50.10.10.10/32")
		
		when:
		rule.removeIpRange("50.10.10.10/32")

		then:
		rule.getIpRange().size() == 0
	}

	def "remove an ipRange via List from a rule"() {
		setup:
		SecurityGroupRule rule = new OpenstackSecurityGroupRule(null, null, null)
		rule.addIpRange("50.10.10.10/32")
		def ipranges = new ArrayList<String>()
		ipranges << "127.0.0.1"
		ipranges << "50.10.10.10/32"

		when:
		rule.removeIpRange(ipranges)

		then:
		rule.getIpRange().size() == 0
	}

	def "remove an ipRange that does not exist"() {
		setup:
		SecurityGroupRule rule = new OpenstackSecurityGroupRule(null, null, null)
		rule.addIpRange("50.10.10.10/32")
		
		when:
		rule.removeIpRange("127.0.0.1")

		then:
		rule.getIpRange().size() == 1
	}

	def "remove an ipRange that does not exist"() {
		setup:
		SecurityGroupRule rule = new OpenstackSecurityGroupRule(null, null, null)
		rule.addIpRange("50.10.10.10/32")
		
		when:
		rule.removeIpRange("127.0.0.1")

		then:
		rule.getIpRange().size() == 1
	}

	private createNetworkProvider() {
		OpenstackNetworkProvider.create(
			provider:'openstack',
			username:System.getProperty('openstack.username'),
			password:System.getProperty('openstack.password'),
			apiKey:System.getProperty('openstack.apiKey'),
			identityUrl:System.getProperty('openstack.identityUrl'),
			tenantName:System.getProperty('openstack.tenantName')
		)
	}
}