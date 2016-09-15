package com.bertramlabs.plugins.karman.network

/**
 * Created by davidestes on 3/1/16.
 */
public abstract class SecurityGroupRule implements SecurityGroupRuleInterface{
	private SecurityGroupInterface securityGroup
	private Integer minPort
	private Integer maxPort
	private String ipProtocol
	private List<String> ipRange = new ArrayList<String>() 

	public SecurityGroupRule(SecurityGroupInterface securityGroup) {
		this.securityGroup = securityGroup
	}

	@Override
	public SecurityGroupInterface getSecurityGroup() {
		return securityGroup
	}

	@Override
	public void addIpRange(String ipRange) {

	}

	@Override
	public void addIpRange(List<String> ipRange) {

	}

	@Override
	public void removeIpRange(String ipRange) {

	}

	@Override
	public void removeIpRange(List<String> ipRange) {

	}

	@Override
	public List<String> getIpRange() {
		ipRange
	}

	@Override
	public void setMinPort(Integer port) {
		this.minPort = port
	}

	@Override
	public Integer getMinPort() {
		return minPort
	}

	@Override
	public void setMaxPort(Integer port) {
		this.maxPort = port
	}

	@Override
	public Integer getMaxPort() {
		return maxPort
	}

	@Override
	public void setIpProtocol(String protocol) {
		this.ipProtocol = protocol?.toLowerCase()
	}

	@Override
	public String getIpProtocol() {
		return this.ipProtocol
	}

	@Override
	public void save() {

	}

	@Override
	public void delete() {

	}
}
