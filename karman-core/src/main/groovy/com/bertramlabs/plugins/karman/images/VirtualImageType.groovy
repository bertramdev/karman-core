package com.bertramlabs.plugins.karman.images

/**
 * Representation of all common Virtual Image types across various clouds
 * @see VirtualImageInterface
 * @author David Estes
 */
public enum VirtualImageType {
	/**
	 * Representation of an Amazon Web Services virtual image format (known commonly as an AMI)
	 */
	AMI("ami"),

	/**
	 * Representation of a VMWare based virtual Image (Note some implementations of Openstack are able to use this or even generic ESXI)
	 * If the image is an OVA or OVF they all should be generalized to VMDK.
	 */
	VMDK("vmdk"),

	/**
	 * Representation of the QCOW2 image format commonly used by KVM based hypervisors
	 */
	QCOW2("qcow2"),

	/**
	 * Representation of a RAW image format used by several different hypervisors and facilitates easier conversion
	 */
	RAW("raw"),

	/**
	 * VHD Image format representation commonly used bu Xen as well as Hyper-V based hosts (Microsoft Azure as well).
	 */
	VHD("vhd"),

	/**
	 * A more compact VHD image format only supported by Hyper-V
	 */
	VHDX("vhdx"),

	/**
	 * Xen image snapshot format (cannot be imported but only exported).
	 */
	XVA("xva"),

	/**
	 * CDROM Image Format ISO9660 typically.
	 */
	ISO("iso")
}