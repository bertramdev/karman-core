package com.bertramlabs.plugins.karman.network

/**
 * Created by davidestes on 3/1/16.
 */
public interface SecurityGroupRuleInterface {
	public SecurityGroupInterface getSecurityGroup()

	public void addIpRange(String ipRange)
	public void addIpRange(List<String> ipRange)

	public void removeIpRange(String ipRange)
	public void removeIpRange(List<String> ipRange)

	public void setMinPort(Integer port)
	public Integer getMinPort()

	public void setMaxPort(Integer port)
	public Integer getMaxPort()

	public void setIpProtocol(String protocol)
	public String getIpProtocol()

	public void save()

}