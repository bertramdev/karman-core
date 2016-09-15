package com.bertramlabs.plugins.karman.network

/**
 * Provides a base interface for the Network Provider. The Network provider is the base connection provider for interacting with various cloud services pertaining to network configuration functionality.
 * Primarily this deals with management of security groups across various cloud types.
 * @author David Estes
 */

public interface NetworkProviderInterface {
	public String getProviderName()

	public Collection<SecurityGroupInterface> getSecurityGroups(Map options)
	public Collection<SecurityGroupInterface> getSecurityGroups()

	public SecurityGroupInterface getSecurityGroup(String uid)

	public SecurityGroupInterface createSecurityGroup(String name)

}