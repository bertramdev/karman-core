package com.bertramlabs.plugins.karman.openstack

import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.openstack.OpenstackNetworkProvider
import spock.lang.Specification
import spock.lang.*

class OpenstackNetworkProviderSpec extends Specification {
	
	@Shared	
	OpenstackNetworkProvider networkProvider

	def setup() {
		this.networkProvider = createNetworkProvider()
	}

	def "network provider creates"() {
		when:
		networkProvider
		
		then:
		networkProvider
	}
	
	def "find all endpoints when only one version of the endpoint type exists"() {
		when:
		def result = networkProvider.findEndpointsForType(catalogSample, "compute")
		
		then:
		result.size() == 1
	}
	
	def "find all endpoints when more than one version of the endpoint type exists"() {
		when:
		def result = networkProvider.findEndpointsForType(catalogSample, "volume")
		
		then:
		result.size() == 2
	}
	
	def "find the latest version of a set of endpoints"() {
		when:
		def endpointsOfType = networkProvider.findEndpointsForType(catalogSample, "volume")
		def result = networkProvider.findLatestEndpointInSet(endpointsOfType)
		
		then:
		result.type == "volumev2"
	}
	
	def "find the latest version of a set 3 of endpoints"() {
		when:
		def endpoints = [[type: 'volume'], [type: 'volumev3'], [type:'volumev2']]
		def result = networkProvider.findLatestEndpointInSet(endpoints)
		
		then:
		result.type == "volumev3"
	}
	
	def "setEndpoints with a missing endpoint produces an accessible error"() {
		when:
		Map tokenResults = [token: [catalog: [
			[type: 'volume', endpoints: [[url: "http://127.0.0.1:0001"]]], 
			[type: 'storage', endpoints: [[url: "http://127.0.0.1:0001"]]], 
			[type:'compute', endpoints: [[url: "http://127.0.0.1:0001"]]],
			[type:'image', endpoints: [[url: "http://127.0.0.1:0001"]]]
		]]]
		networkProvider.setIdentityUrl("http://127.0.0.1:0001/v3")
		networkProvider.setAccessInfo([identityApiVersion: 'v3'])
		def result = networkProvider.setEndpoints(tokenResults)
		
		then:
		result.errors.size() == 1
		result.errors?.getAt(0)?.get('network') != null
	}
	
	def "findEndpoint: parsing endpoints with an error returns nothing."() {
		when:
		def result = networkProvider.findEndpoint([], "http://127.0.0.1:0001")
		
		then:
		result == null
	}
	
	def "findEndpoint: returns publicURL endpoint first"() {
		when:
		def endpoints = [[publicURL: 'http://127.0.0.1:0001', url: 'http://10.0.0.1:0001', adminURL: 'http://10.0.0.1:0001']]
		def result = networkProvider.findEndpoint(endpoints, "http://127.0.0.1:0001")
		
		then:
		result == endpoints.getAt(0).publicURL
	}
	
	def "findEndpoint: returns url if publicURL doens't contiain host"() {
		when:
		def endpoints = [[publicURL: 'http://10.0.0.1:0001', url: 'http://127.0.0.1:0001', adminURL: 'http://10.0.0.1:0001']]
		def result = networkProvider.findEndpoint(endpoints, "http://127.0.0.1:0001")
		
		then:
		result == endpoints.getAt(0).url
	}
	
	def "findEndpoint: returns adminURL if the adminUrl cotains the host and no other endpoint urls contains the host"() {
		when:
		def endpoints = [[publicURL: 'http://10.0.0.1:0001', url: 'http://10.0.0.1:0001', adminURL: 'http://127.0.0.1:0001']]
		def result = networkProvider.findEndpoint(endpoints, "http://127.0.0.1:0001")
		
		then:
		result == endpoints.getAt(0).adminURL
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
	
	static catalogSample = [
		["endpoints": [
			["region_id": "RegionOne", "url": "http://127.0.0.1:9696", "region": "RegionOne", "interface": "admin", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:9696", "region": "RegionOne", "interface": "public", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:9696", "region": "RegionOne", "interface": "internal", "id": "abcdefg12345hijklm6789opqrs456t0"]
		], "type": "network", "id": "abcdefg12345hijklm6789opqrs456t0", "name": "neutron"], 
		["endpoints": [
			["region_id": "RegionOne", "url": "http://127.0.0.1:8776/v1/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "admin", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:8776/v1/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "public", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:8776/v1/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "internal", "id": "abcdefg12345hijklm6789opqrs456t0"]
		], "type": "volume", "id": "abcdefg12345hijklm6789opqrs456t0", "name": "cinder"], 
		["endpoints": [
			["region_id": "RegionOne", "url": "http://127.0.0.1:8774/v2/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "internal", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:8774/v2/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "admin", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:8774/v2/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "public", "id": "abcdefg12345hijklm6789opqrs456t0"]
		], "type": "compute", "id": "abcdefg12345hijklm6789opqrs456t0", "name": "nova"], 
		["endpoints": [
			["region_id": "RegionOne", "url": "http://127.0.0.1:5000/v2.0", "region": "RegionOne", "interface": "public", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:35357/v2.0", "region": "RegionOne", "interface": "admin", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:5000/v2.0", "region": "RegionOne", "interface": "internal", "id": "abcdefg12345hijklm6789opqrs456t0"]
		], "type": "identity", "id": "abcdefg12345hijklm6789opqrs456t0", "name": "keystone"], 
		["endpoints": [
			["region_id": "RegionOne", "url": "http://127.0.0.1:9292", "region": "RegionOne", "interface": "internal", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:9292", "region": "RegionOne", "interface": "admin", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:9292", "region": "RegionOne", "interface": "public", "id": "abcdefg12345hijklm6789opqrs456t0"]
		], "type": "image", "id": "abcdefg12345hijklm6789opqrs456t0", "name": "glance"], 
		["endpoints": [
			["region_id": "RegionOne", "url": "http://127.0.0.1:8776/v2/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "admin", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:8776/v2/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "public", "id": "abcdefg12345hijklm6789opqrs456t0"], 
			["region_id": "RegionOne", "url": "http://127.0.0.1:8776/v2/abcdefg12345hijklm6789opqrs456t0", "region": "RegionOne", "interface": "internal", "id": "abcdefg12345hijklm6789opqrs456t0"]
		], "type": "volumev2", "id": "abcdefg12345hijklm6789opqrs456t0", "name": "cinderv2"]
	]
}
