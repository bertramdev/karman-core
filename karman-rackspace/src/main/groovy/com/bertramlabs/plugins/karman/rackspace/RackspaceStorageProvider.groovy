package com.bertramlabs.plugins.karman.rackspace

import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.openstack.OpenstackStorageProvider
import com.bertramlabs.plugins.karman.StorageProvider
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils

/**
 * Storage provider implementation for the Rackspace Cloud Files API
 * This is the starting point from which all calls to rackspace originate for storing files within the Cloud File Containers
 * <p>
 * Below is an example of how this might be initialized.
 * </p>
 * <pre>
 * {@code
 * import com.bertramlabs.plugins.karman.StorageProvider
 * def provider = StorageProvider(
 *  provider: 'rackspace',
 *  username: 'myusername',
 *  apiKey: 'rackspace-api-key-here',
 *  region: 'IAD'
 * )
 *
 * //Shorthand
 * provider['container']['example.txt'] = "This is a string I am storing."
 * //or
 * provider.'container'.'example.txt' = "This is a string I am storing."
 * }
 * </pre>
 *
 * @author David Estes
 */
@Slf4j
public class RackspaceStorageProvider extends OpenstackStorageProvider {
	static String providerName = "rackspace"

	public RackspaceStorageProvider(Map options) {
		super(options);
		if(!identityUrl) {
			identityUrl = 'https://identity.api.rackspacecloud.com/v2.0'
		}
	}
}
