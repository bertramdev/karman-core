package com.bertramlabs.plugins.karman.azure

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressResult
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressResult
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.ec2.model.RevokeSecurityGroupEgressRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupEgressResult
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressResult
import com.bertramlabs.plugins.karman.network.SecurityGroupInterface
import com.bertramlabs.plugins.karman.network.SecurityGroupRule
import com.bertramlabs.plugins.karman.network.SecurityGroupRuleInterface
import groovy.util.logging.Commons
import sun.reflect.generics.reflectiveObjects.NotImplementedException

@Commons
class AzureSecurityGroupRule extends SecurityGroupRule {
	private AzureNetworkProvider provider
	private AzureSecurityGroup azureSecurityGroup

	private String name
	private String id
	private Object properties
	private String provisioningState
	private String sourceAddressPrefix
	private List<String> sourceAddressPrefixes = new ArrayList<String>()
	private String destinationAddressPrefix
	private List<String> destinationAddressPrefixes = new ArrayList<String>()
	private List<String> destinationApplicationSecurityGroups = new ArrayList<String>()
	private String access
	private String destinationPortRange
	private List<String>  destinationPortRanges= new ArrayList<String>()
	private String direction
	private Integer priority
	private List<String> sourceApplicationSecurityGroups = new ArrayList<String>()
	private String sourcePortRange
	private List<String> sourcePortRanges = new ArrayList<String>()

	private boolean modified = false

	AzureSecurityGroupRule(AzureNetworkProvider provider, SecurityGroupInterface securityGroup, Map options) {
		super(securityGroup)

		this.provider = provider
		this.azureSecurityGroup = securityGroup

		initializeFromOptions(options)
	}

	private void initializeFromOptions(Map options) {
		name = options?.name
		id = options?.id
		properties = options?.properties
		provisioningState = properties?.provisioningState
		access = properties?.access ?: 'Allow'
		description = properties?.description
		ipProtocol = properties?.protocol ?: '*'
		minPort = options?.minPort
		maxPort = options?.maxPort
		sourceAddressPrefix = properties?.sourceAddressPrefix
		sourceAddressPrefixes = properties?.sourceAddressPrefixes
		destinationAddressPrefix = properties?.destinationAddressPrefix
		destinationAddressPrefixes = properties?.destinationAddressPrefixes
		destinationApplicationSecurityGroups = properties?.destinationApplicationSecurityGroups
		destinationPortRange = properties?.destinationPortRange
		destinationPortRanges = properties?.destinationPortRanges
		direction = properties?.direction?.toString()?.toLowerCase() == 'inbound' ? 'ingress' : 'egress'
		priority = properties?.priority ? properties?.priority?.toString()?.toInteger() : null
		sourceApplicationSecurityGroups = properties?.sourceApplicationSecurityGroups
		sourcePortRange = properties?.sourcePortRange
		sourcePortRanges = properties?.sourcePortRanges

		def portRange = direction == 'ingress' ? destinationPortRange : sourcePortRange
		if(portRange) {
			if(portRange.contains('-')) {
				def parts = portRange.tokenize('-')
				minPort = parts[0].toString().toInteger()
				maxPort = parts[1].toString().toInteger()
			} else {
				if(portRange == '*') {
					minPort = 0
					maxPort = 65535
				} else {
					minPort = portRange.toString().toInteger()
					maxPort = minPort
				}
			}
		}

		def addressPrefix = direction == 'ingress' ? sourceAddressPrefix : destinationAddressPrefix
		if(addressPrefix) {
			if(addressPrefix == '*') {
				ipRange << '0.0.0.0/0'
			} else if(addressPrefix.contains(',')) {
				addressPrefix.tokenize()?.each {
					ipRange << it
				}
			} else if(addressPrefix =~ /^([0-9]{1,3}\.){3}[0-9]{1,3}(\/([0-9]|[1-2][0-9]|3[0-2]))?$/) {
				ipRange << addressPrefix
			}
		}
	}

	public void setName(String name) {
		this.name = name
		modified = true
	}

	public String getName() {
		this.name
	}

	public void setId(String id) {
		this.id = id
		modified = true
	}

	@Override
	public String getId() {
		this.id
	}

	public void setProvisioningState(String provisioningState) {
		this.provisioningState = provisioningState
		modified = true
	}

	public String getProvisioningState() {
		this.provisioningState
	}

	public void setSourceAddressPrefix(String sourceAddressPrefix) {
		this.sourceAddressPrefix = sourceAddressPrefix
		modified = true
	}

	public String getSourceAddressPrefix() {
		this.sourceAddressPrefix
	}

	public void setSourceAddressPrefixes(List<String> sourceAddressPrefixes) {
		this.sourceAddressPrefixes = sourceAddressPrefixes
		modified = true
	}

	public List<String> getSourceAddressPrefixes() {
		this.sourceAddressPrefixes
	}

	public void setDestinationAddressPrefix(String destinationAddressPrefix) {
		this.destinationAddressPrefix = destinationAddressPrefix
		modified = true
	}

	public String getDestinationAddressPrefix() {
		this.destinationAddressPrefix
	}

	public void setDestinationAddressPrefixes(List<String> destinationAddressPrefixes) {
		this.destinationAddressPrefixes = destinationAddressPrefixes
		modified = true
	}

	public List<String> getDestinationAddressPrefixes() {
		this.destinationAddressPrefixes
	}

	public void setDestinationApplicationSecurityGroups(List<String> destinationApplicationSecurityGroups) {
		this.destinationApplicationSecurityGroups = destinationApplicationSecurityGroups
		modified = true
	}

	public List<String> getDestinationApplicationSecurityGroups() {
		this.destinationApplicationSecurityGroups
	}

	public void setAccess(String access) {
		this.access = access
		modified = true
	}

	public String getAccess() {
		this.access
	}

	public void setPriority(Integer priority) {
		this.priority = priority
		modified = true
	}

	public Integer getPriority() {
		this.priority
	}

	public void setDestinationPortRange(String destinationPortRange) {
		this.destinationPortRange = destinationPortRange
		modified = true
	}

	public String getDestinationPortRange() {
		this.destinationPortRange
	}

	public void setDestinationPortRanges(List<String> destinationPortRanges) {
		this.destinationPortRanges = destinationPortRanges
		modified = true
	}

	public List<String> getDestinationPortRanges() {
		this.destinationPortRanges
	}


	public void setSourceApplicationSecurityGroups(List<String> sourceApplicationSecurityGroups) {
		this.sourceApplicationSecurityGroups = sourceApplicationSecurityGroups
		modified = true
	}

	public List<String> getSourceApplicationSecurityGroups() {
		this.sourceApplicationSecurityGroups
	}


	public void setSourcePortRange(String sourcePortRange) {
		this.sourcePortRange = sourcePortRange
		modified = true
	}

	public String getSourcePortRange() {
		this.sourcePortRange
	}

	public void setSourcePortRanges(List<String> sourcePortRanges) {
		this.sourcePortRanges = sourcePortRanges
		modified = true
	}

	public List<String> getSourcePortRanges() {
		this.sourcePortRanges
	}

	@Override
	public void addIpRange(String ipRange) {
		this.ipRange << ipRange
		modified = true
	}

	@Override
	public void addIpRange(List<String> ipRange) {
		// OpenStack only supports a single cidr
		if(ipRange.size()) {
			this.ipRange << ipRange.first()
		}
		modified = true
	}

	@Override
	public void removeIpRange(String ipRange) {
		this.ipRange.remove(ipRange)
		modified = true
	}

	@Override
	public void removeIpRange(List<String> ipRange) {
		ipRange.each { it ->
			this.ipRange.remove(it)
		}
		modified = true
	}

	@Override
	public void setMinPort(Integer port) {
		super.setMinPort(port)
		modified = true
	}

	@Override
	public void setMaxPort(Integer port) {
		super.setMaxPort(port)
		modified = true
	}

	@Override
	public void setIpProtocol(String protocol) {
		super.setIpProtocol(protocol)
		modified = true
	}

	@Override
	void setEthertype(String ethertype) {

	}

	@Override
	String getEthertype() {
		return null
	}

	@Override
	void setPolicy(String policy) {
		super.setPolicy(policy)
		modified = true
	}

	@Override
	void setTargetGroupName(String groupName) {
		super.setTargetGroupName(groupName)
		modified = true
	}
	@Override
	void setTargetGroupId(String groupId) {
		super.setTargetGroupId(groupId)
		modified = true
	}


	@Override
	void setDirection(String targetDirection) {
		this.direction = targetDirection?.toLowerCase()
		modified = true
	}

	@Override
	String getDirection() {
		return this.direction
	}

	@Override
	void setDescription(String targetDescription) {
		super.setDescription(targetDescription)
		modified = true
	}

	@Override
	void save() {
		if(modified) {
			// Must always start with the payload from Azure and then overlay our changes on top
			def apiPath = "/subscriptions/${provider.subscriptionId}/resourceGroups/${azureSecurityGroup.getResourceGroup()}/providers/Microsoft.Network/networkSecurityGroups/${securityGroup.name}/securityRules/${name}"

			def body = [:]
			if (this.id) {
				def results = provider.callApi(apiPath, [query: ['api-version': '2018-11-01']], 'GET')
				def parsedResult = new groovy.json.JsonSlurper().parseText(results.content)
				if (parsedResult.error) {
					throw new RuntimeException("Error getting existing Security Group Rule: ${parsedResult.error?.message}")
				}
				body = parsedResult
			}

			body.properties = body.properties ?: [:]
			body.properties.sourceAddressPrefix = body.properties.sourceAddressPrefix ?: ''
			body.properties.sourceAddressPrefixes = body.properties.sourceAddressPrefixes ?: []
			body.properties.destinationAddressPrefix = body.properties.destinationAddressPrefix ?: ''
			body.properties.destinationAddressPrefixes = body.properties.destinationAddressPrefixes ?: []
			body.properties.sourcePortRange = body.properties.sourcePortRange ?: ''
			body.properties.sourcePortRanges = body.properties.sourcePortRanges ?: []

			body.properties.protocol = ipProtocol?.toLowerCase() == 'any' || ipProtocol?.toLowerCase() == 'all' ? '*' : ipProtocol
			body.properties.description = description
			body.properties.access = access
			body.properties.direction = direction == 'ingress' ? 'Inbound' : 'Outbound'

			if (priority != null) {
				body.properties.priority = priority
			}
			if(body.properties.priority == null) {
				// Get a unique Id
				def priorities = securityGroup.rules?.collect { AzureSecurityGroupRule rule ->
					rule.priority
				}.flatten()
				def priorityValue
				def i = 100
				while(!priorityValue && i < 4096){
					if(!priorities.contains(i)) {
						priorityValue = i
					}
					i++
				}
				body.properties.priority = priorityValue
			}

			def addressPrefix
			if (ipRange.size() == 1) {
				if (ipRange[0] == '0.0.0.0/0') {
					addressPrefix = '*'
				} else if(ipRange[0] =~ /^([0-9]{1,3}\.){3}[0-9]{1,3}(\/([0-9]|[1-2][0-9]|3[0-2]))?$/) {
					addressPrefix = ipRange[0]
				}
			} else {
				addressPrefix = ipRange.join(',')
			}

			if (addressPrefix) {
				if (direction == 'ingress') {
					if (addressPrefix.contains(',')) {
						body.properties.sourceAddressPrefixes = addressPrefix.tokenize(',')
						body.properties.remove('sourceAddressPrefix')
					} else {
						body.properties.sourceAddressPrefix = addressPrefix
						body.properties.remove('sourceAddressPrefixes')
					}
				} else {
					if (addressPrefix.contains(',')) {
						body.properties.destinationAddressPrefixes = addressPrefix.tokenize(',')
						body.properties.remove('destinationAddressPrefix')
					} else {
						body.properties.destinationAddressPrefix = addressPrefix
						body.properties.remove('destinationAddressPrefixes')
					}
				}
			} else {
				// Must be using tags or non-cidr source/destination prefixes
				body.properties.sourceAddressPrefix = sourceAddressPrefix
				body.properties.sourceAddressPrefixes = sourceAddressPrefixes
				body.properties.destinationAddressPrefix = destinationAddressPrefix
				body.properties.destinationAddressPrefixes = destinationAddressPrefixes
			}

			if(minPort != null || maxPort != null) {
				minPort = minPort != null ? minPort : maxPort
				maxPort = maxPort != null ? maxPort : minPort
				def portRange
				if (minPort == 0 && maxPort == 65535) {
					portRange = '*'
				} else if (minPort == maxPort) {
					portRange = minPort.toString()
				} else {
					portRange = "${minPort}-${maxPort}"
				}
				if (direction == 'ingress') {
					body.properties.destinationPortRange = portRange
					body.properties.destinationPortRanges = []
				} else {
					body.properties.sourcePortRange = portRange
					body.properties.sourcePortRanges = []
				}
			} else {
				body.properties.sourcePortRange = sourcePortRange
				body.properties.sourcePortRanges = sourcePortRanges
				body.properties.destinationPortRange = destinationPortRange
				body.properties.destinationPortRanges = destinationPortRanges
			}

			if (destinationApplicationSecurityGroups) {
				body.properties.destinationApplicationSecurityGroups = destinationApplicationSecurityGroups
			}

			if(!body.properties.destinationPortRange && !body.properties.destinationPortRanges){
				body.properties.destinationPortRange = '*'
			}

			if(!body.properties.destinationAddressPrefix && !body.properties.destinationAddressPrefixes){
				body.properties.destinationAddressPrefix = '*'
			}

			if(!body.properties.sourcePortRange && !body.properties.sourcePortRanges){
				body.properties.sourcePortRange = '*'
			}

			if(!body.properties.sourceAddressPrefix && !body.properties.sourceAddressPrefixes){
				body.properties.sourceAddressPrefix = '*'
			}

			def results = provider.callApi(apiPath, [query: ['api-version': '2018-11-01'], body: body], 'PUT')
			def parsedResult = new groovy.json.JsonSlurper().parseText(results.content)
			if (parsedResult.error) {
				throw new RuntimeException("Error saving Security Group Rule: ${parsedResult.error?.message}")
			}
			initializeFromOptions(parsedResult)
			modified = false
		}
	}

	@Override
	void delete() {
		if(id) {
			def results = provider.callApi(id, [query: ['api-version': '2018-11-01']], 'DELETE')
			def parsedResult = new groovy.json.JsonSlurper().parseText(results.content ?: '{}')
			if (parsedResult.error) {
				throw new RuntimeException("Error deleting Security Group Rule: ${parsedResult.error?.message}")
			}
			id = null
		}
	}

}
