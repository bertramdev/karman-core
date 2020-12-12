/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bertramlabs.plugins.karman.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3EncryptionClient
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.Bucket
import com.amazonaws.services.s3.model.CryptoConfiguration
import com.amazonaws.services.s3.model.CryptoStorageMode
import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.model.EncryptionMaterials
import com.amazonaws.services.s3.model.StaticEncryptionMaterialsProvider
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.services.securitytoken.model.AssumeRoleResult
import com.bertramlabs.plugins.karman.Directory
import com.amazonaws.auth.AnonymousAWSCredentials
import com.bertramlabs.plugins.karman.StorageProvider
import org.apache.commons.beanutils.PropertyUtils
import org.apache.http.HttpHost
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.SSLContextBuilder
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.protocol.HttpContext

import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

import java.lang.reflect.InvocationTargetException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class S3StorageProvider extends StorageProvider {

    static String providerName = "s3"
    static SSLContext sslcontext
    static SSLConnectionSocketFactory sslConnectionFactory
    

    String accessKey = ''
    String secretKey = ''
	Boolean useHostCredentials = false
	String stsAssumeRole
    String token = ''
    String region = ''
    String endpoint = ''
    String baseUrl
    Map<String,String> baseUrls
    String symmetricKey
    String protocol = 'https'
    String proxyHost
    Integer proxyPort
    String proxyUser
    String proxyPassword
    String proxyWorkstation
    String proxyDomain
    Integer maxConnections = 50
    Boolean keepAlive = false
    Boolean useGzip = false
    Boolean anonymous = false
    Boolean forceMultipart = false
	Boolean disableChunkedEncoding = false
    Boolean pathStyleAccess = false
	private Date clientExpires=null
    AmazonS3Client client = null
    Long chunkSize = 100l*1024l*1024l

    static {
         sslcontext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                @Override
                boolean isTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                    return true
                }
        }).build()
        sslConnectionFactory = new SSLConnectionSocketFactory(sslcontext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER) {
            @Override
            Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException, ConnectTimeoutException {
                if(socket instanceof SSLSocket) {
                    try {
                        socket.setEnabledProtocols(['SSLv3', 'TLSv1', 'TLSv1.1', 'TLSv1.2'] as String[])
                        PropertyUtils.setProperty(socket, "host", host.getHostName())
                    } catch (NoSuchMethodException ex) {}
                    catch (IllegalAccessException ex) {}
                    catch (InvocationTargetException ex) {}
                    catch (Exception ex) {
                        
                    }
                }
                return super.connectSocket(30000, socket, host, remoteAddress, localAddress, context)
            }
        }
    }

    public S3StorageProvider(Map options) {
        accessKey      = options.accessKey      ?: accessKey
        secretKey      = options.secretKey      ?: secretKey
		useHostCredentials = options.useHostCredentials ?: useHostCredentials
		stsAssumeRole = options.stsAssumeRole ?: stsAssumeRole
        token          = options.token          ?: token
        region         = options.region         ?: region
        endpoint       = options.endpoint       ?: endpoint
        symmetricKey   = options.symmetricKey   ?: symmetricKey
        protocol       = options.protocol       ?: protocol
        maxConnections = options.maxConnections ?: maxConnections
        keepAlive      = options.keepAlive      ?: keepAlive
        defaultFileACL = options.defaultFileACL ?: defaultFileACL
        useGzip        = options.useGzip        ?: useGzip
        forceMultipart = options.forceMultipart ?: forceMultipart
		disableChunkedEncoding = options.disableChunkedEncoding ?: disableChunkedEncoding
        pathStyleAccess = options.pathStyleAccess ?: pathStyleAccess

        anonymous = options.anonymous ?: anonymous

        baseUrl = options.baseUrl ?: baseUrl
        baseUrls = options.baseUrls ?: baseUrls
        proxyHost = options.proxyHost ?: proxyHost
        proxyPort = options.proxyPort ?: proxyPort
        proxyUser = options.proxyUser ?: proxyUser
        proxyPassword = options.proxyPassword ?: proxyPassword
        proxyDomain = options.proxyDomain ?: proxyDomain
        proxyWorkstation = options.proxyWorkstation ?: proxyWorkstation
        chunkSize = options.chunkSize ?: chunkSize
		tempDir = options.tempDir ?: tempDir
    }

    Directory getDirectory(String name) {
        new S3Directory(name: name, provider: this)
    }

    List<Directory> getDirectories() {
        List<Bucket> buckets = s3Client.listBuckets()
        buckets.collect { bucket -> directoryFromS3Bucket(bucket)}
    }

    AmazonS3Client getS3Client() {

        if(client) {
			if(clientExpires == null || clientExpires > new Date()) {
				return client
			}
        }

        AWSCredentials credentials = null
		if (accessKey && secretKey && token) {
            credentials = new BasicSessionCredentials (accessKey, secretKey, token)
        }
        else if (accessKey && secretKey && !token) {
            credentials = new BasicAWSCredentials(accessKey, secretKey)
        } else if(anonymous){
            credentials = new AnonymousAWSCredentials()
        }

        final AWSCredentialsProvider credentialsProvider

        if (credentials) {
            credentialsProvider = new AWSStaticCredentialsProvider(credentials)
        } else {
			if(useHostCredentials) {
				credentialsProvider = new InstanceProfileCredentialsProvider()

			} else {
				credentialsProvider = new DefaultAWSCredentialsProviderChain()
			}

        }
		if(stsAssumeRole) {
			AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder.standard().withCredentials(credentialsProvider).withRegion(region).build()
			AssumeRoleResult roleResult = sts.assumeRole(new AssumeRoleRequest().withRoleArn(stsAssumeRole).withRoleSessionName('karman'))

			if(roleResult.credentials) {
				credentialsProvider = null
			}
			def roleCredentials = roleResult.credentials
			credentials = new BasicSessionCredentials(roleCredentials.getAccessKeyId(), roleCredentials.getSecretAccessKey(), roleCredentials.getSessionToken());
			clientExpires = roleCredentials.getExpiration()
		}

        ClientConfiguration configuration = new ClientConfiguration()
        configuration.setUseTcpKeepAlive(keepAlive)
        configuration.setMaxConnections(maxConnections)
        configuration.setProtocol(protocol == 'https' ? com.amazonaws.Protocol.HTTPS : com.amazonaws.Protocol.HTTP)
        if(proxyHost) {
            configuration.setProxyHost(proxyHost)
        }
        if(proxyPort) {
            configuration.setProxyPort(proxyPort)
        }
        if(proxyUser) {
            configuration.setProxyUsername(proxyUser)
        }
        if(proxyPassword) {
            configuration.setProxyPassword(proxyPassword)
        }
        if(proxyDomain) {
            configuration.setProxyDomain(proxyDomain)
        }
        if(proxyWorkstation) {
            configuration.setProxyWorkstation(proxyWorkstation)
        }

        configuration.setUseGzip(useGzip)
		if (endpoint) {
			configuration.getApacheHttpClientConfig().setSslSocketFactory(sslConnectionFactory)
		}
		S3ClientOptions clientOptions = new S3ClientOptions()
		if(disableChunkedEncoding) {
			clientOptions.disableChunkedEncoding()
		}
        if(pathStyleAccess) {
            clientOptions.setPathStyleAccess(pathStyleAccess)
        }

        if(symmetricKey) {
            EncryptionMaterials materials = new EncryptionMaterials(new SecretKeySpec(symmetricKey.bytes,'AES'))
            CryptoConfiguration cryptoConfig = new CryptoConfiguration().withStorageMode(CryptoStorageMode.ObjectMetadata)
			if(credentialsProvider) {
				client = new AmazonS3EncryptionClient(credentialsProvider,
					new StaticEncryptionMaterialsProvider(materials),
					configuration,
					cryptoConfig)
			} else if(credentials) {
				client = new AmazonS3EncryptionClient(credentials,new StaticEncryptionMaterialsProvider(materials),configuration,cryptoConfig)
			}



        } else {
			if(credentialsProvider) {
				client = new AmazonS3Client(credentialsProvider, configuration)
			} else if (credentials) {
				client = new AmazonS3Client(credentials, configuration)
			}

        }
		client.setS3ClientOptions(clientOptions)

        if (region) {
            Region region = RegionUtils.getRegion(region)
            client.region = region
        }
        if (endpoint) {
            client.endpoint = endpoint
        }

        client
    }

    public void shutdown() {
        if(client) {
            client.shutdown()
        }
    }

    // PRIVATE

    private S3Directory directoryFromS3Bucket(bucket) {
        new S3Directory(
				bucket: bucket,
                name: bucket.name,
                provider: this
        )
    }

}