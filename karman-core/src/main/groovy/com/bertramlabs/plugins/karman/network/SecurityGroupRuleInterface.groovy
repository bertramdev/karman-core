package com.bertramlabs.plugins.karman.network

/**
 * Created by davidestes on 3/1/16.
 */
interface SecurityGroupRuleInterface {
	String getId()
	String getPolicy()
	void setPolicy(String policy)

	String getDirection()
	void setDirection(String targetDirection)

	String getDescription()
	void setDescription(String targetDescription)

	SecurityGroupInterface getSecurityGroup()
	void addIpRange(String ipRange)
	void addIpRange(List<String> ipRange)

	void removeIpRange(String ipRange)
	void removeIpRange(List<String> ipRange)

	List<String> getIpRange()

	void setMinPort(Integer port)
	Integer getMinPort()

	void setMaxPort(Integer port)
	Integer getMaxPort()

	void setIpProtocol(String protocol)
	String getIpProtocol()


	void setEthertype(String ethertype)
	String getEthertype()

	void setTargetGroupName(String groupName)
	void setTargetGroupId(String groupId)
	String getTargetGroupName()
	String getTargetGroupId()


	void save()

	void delete()




}