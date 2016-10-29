package com.bertramlabs.plugins.karman.util

/**
 * Object representation of an Operating System specification within Karman
 * Provides useful information about what guest operating system is running on a specific virtual machine and/or a virtual image.
 *
 * @author David Estes
 */
public class OperatingSystem {
	/**
	 * The common name of the operating system
	 */
	String name

	/**
	 * The Operating system version (if it can be determined) that this record represents
	 */
	String version

	/**
	 * The vendor or distributor of the operating system. (i.e. canonical, microsoft, redhat)
	 */
	String vendor

	/**
	 * The family of operating system this is running (a more generic operating system category representation)
	 * @see {@link OsFamily}
	 */
	String family = OsFamily.LINUX_GENERIC

	/**
	 * The {@link Architecture} is a representation of what cpu platform the operating system is designed for
	 * (i.e. X86 or X86_64, even ARM based architectures can be specified
	 */
	Architecture arch = Architecture.X86


	public static OperatingSystem windowsInstance(Architecture arch, String version=null) {
		new OperatingSystem(name: 'Windows', version: version, arch:arch, family: OsFamily.WINDOWS, vendor: 'Microsoft')
	}

	public static OperatingSystem linuxInstance(Architecture arch, String version=null) {
		new OperatingSystem(name: 'Linux', version: version, arch:arch, family: OsFamily.LINUX_GENERIC, vendor: null)
	}
}

/**
 * An enum representing The CPU Architecture of a specific platform. Primarily used when representing an {@link OperatingSystem}
 *
 * @author David Estes
 */
public enum Architecture {

	/**
	 * Representation of a standard x86 32-bit architecture.
	 */
	X86("x86"),

	/**
	 * Most common 64-bit x86 architectural representation.
	 */
	X86_64("x86_64"),

	/**
	 * Generic ARM architecture representation
	 */
	ARM("arm"),

	/**
	 * ARM v6 platform representation (less common these days)
	 */
	ARM_V6("arm_v6"),

	/**
	 * ARM 7 Architecture representation (currently there are no markers for 64-bit ARM this needs filled out)
	 */
	ARM_V7("arm_v7"),

	/**
	 * ARM v8 Architectural representation
	 */
	ARM_V8("arm_v8")

	/** The header value representing the canned acl */
	private final String architecture

	private Architecture(String architecture) {
		this.architecture = architecture
	}

	/**
	 * Returns the header value for this canned acl.
	 */
	public String toString() {
		return architecture
	}

}

/**
 * An enum representing a more generic operating system family rather than specific operating system versions.
 * This can range from unix based, to Windows based families.
 * @see OperatingSystem
 * @author David Estes
 */
public enum OsFamily {
	DEBIAN("debian"),
	REDHAT("rhel"),
	GENTOO("gentoo"),
	LINUX_GENERIC("linux"),
	WINDOWS("windows"),
	MACOS("macos"),
	BSD("bsd"),
	REDOX("redox"),
	OTHER("other")

	/** The header value representing the canned acl */
	private final String osFamily

	private OsFamily(String osFamily) {
		this.osFamily = osFamily
	}

	/**
	 * Returns the header value for this canned acl.
	 */
	public String toString() {
		return osFamily
	}
}