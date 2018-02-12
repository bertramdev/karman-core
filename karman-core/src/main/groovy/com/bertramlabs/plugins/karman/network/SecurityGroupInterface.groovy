package com.bertramlabs.plugins.karman.network

/**
 * Created by davidestes on 3/1/16.
 */
public interface SecurityGroupInterface {

	public NetworkProvider getProvider()

	public String getId()


	public String getName()
	public void setName(String name)

	public String getDescription()
	public void setDescription(String description)

	public Collection<SecurityGroupRuleInterface> getRules()

	public SecurityGroupRuleInterface createRule()

	public void removeRule(SecurityGroupRuleInterface rule)

	public void clearRules()

	public void save()

	public void delete()

	public String getMd5Hash()

}