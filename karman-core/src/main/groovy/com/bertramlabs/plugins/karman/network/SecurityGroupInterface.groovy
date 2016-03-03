package com.bertramlabs.plugins.karman.network

/**
 * Created by davidestes on 3/1/16.
 */
public interface SecurityGroupInterface {
	public String getId()


	public String getName()
	public void setName(String name)

	public String getDescription()
	public void setDescription(String description)

	public Collection<SecurityGroupRuleInterface> getRules()

	public void addRule(SecurityGroupInterface rule)

	public void removeRule(SecurityGroupInterface rule)

	public void clearRules()


	public void save()

}