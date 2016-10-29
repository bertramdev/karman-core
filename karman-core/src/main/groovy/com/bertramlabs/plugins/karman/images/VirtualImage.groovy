package com.bertramlabs.plugins.karman.images

import groovy.transform.CompileStatic

/**
 * Base object representation of a Virtual Image. This class provides methods for interacting with metadata pertaining
 * to Virtual Images as well as uploading / downloading where supported. If a feature is not supported on the specific
 * implementation and it is called an Exception will be thrown.
 *
 * @author David Estes
 */
@CompileStatic
public abstract class VirtualImage implements VirtualImageInterface {

	VirtualImageProvider provider


	/**
	 * Returns the name of the file
	 */
	String toString() {
		return "VirtualImage: ${getName()} - ${getUid()}"
	}
}
