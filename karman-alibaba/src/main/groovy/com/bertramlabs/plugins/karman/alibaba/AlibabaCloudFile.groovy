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

package com.bertramlabs.plugins.karman.alibaba

import com.aliyun.oss.OSSClient
import com.aliyun.oss.model.CannedAccessControlList
import com.aliyun.oss.model.CompleteMultipartUploadRequest
import com.aliyun.oss.model.InitiateMultipartUploadRequest
import com.aliyun.oss.model.InitiateMultipartUploadResult
import com.aliyun.oss.model.OSSObject
import com.aliyun.oss.model.OSSObjectSummary
import com.aliyun.oss.model.ObjectListing
import com.aliyun.oss.model.PartETag
import com.aliyun.oss.model.PutObjectRequest
import com.aliyun.oss.model.UploadPartRequest
import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.CloudFileACL
import com.bertramlabs.plugins.karman.util.ChunkedInputStream
import groovy.util.logging.Commons
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.ParseException
import org.apache.http.auth.AuthScope
import org.apache.http.auth.NTCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.utils.URIBuilder
import org.apache.http.config.MessageConstraints
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.HttpConnectionFactory
import org.apache.http.conn.ManagedHttpClientConnection
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.X509HostnameVerifier
import org.apache.http.entity.InputStreamEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.DefaultHttpResponseFactory
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.ProxyAuthenticationStrategy
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.impl.conn.DefaultHttpResponseParser
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory
import org.apache.http.io.HttpMessageParser
import org.apache.http.io.HttpMessageParserFactory
import org.apache.http.io.HttpMessageWriterFactory
import org.apache.http.io.SessionInputBuffer
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicLineParser
import org.apache.http.message.LineParser
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.CharArrayBuffer
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.http.util.EntityUtils

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.lang.reflect.InvocationTargetException
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate

@Commons
class AlibabaCloudFile extends CloudFile{
		AlibabaDirectory parent
		OSSObject object
		OSSObjectSummary summary // Only set when object is retrieved by listFiles
		InputStream writeableStream
		InputStream rawSourceStream

		private Boolean loaded = false
		private Boolean metaDataLoaded = false
		private Boolean existsFlag = null
		private Long internalContentLength = null
		private Boolean internalContentLengthSet =false
		/**
		 * Meta attributes setter/getter
		 */
		void setMetaAttribute(key, value) {
			switch(key) {
				case 'Cache-Control':
					object.objectMetadata.setCacheControl(value)
					break
				case 'Content-Disposition':
					getOSSObject().objectMetadata.setContentDisposition(value)
					break
				case 'Content-Encoding':
					getOSSObject().objectMetadata.setContentEncoding(value)
					break
				case 'Content-Length':
					getOSSObject().objectMetadata.setContentLength(value)
					break
				case 'Content-MD5':
					getOSSObject().objectMetadata.setContentMD5(value)
					break
				case 'Content-Type':
					getOSSObject().objectMetadata.setContentType(value)
					break
				case 'Expires':
					getOSSObject().objectMetadata.setExpirationTime(value)
					break
				case 'Object-Acl':
					getOSSObject().objectMetadata.setObjectAcl(alibabaConvertedACLRule(value))
					break
				default:
					// User specific meta
					getOSSObject().objectMetadata.userMetadata[key] = value
			}
		}

		private CannedAccessControlList alibabaConvertedACLRule(CloudFileACL acl) {
			switch(acl) {
				case CloudFileACL.Private:
					return CannedAccessControlList.Private
				case CloudFileACL.PublicRead:
					return CannedAccessControlList.PublicRead
				case CloudFileACL.PublicReadWrite:
					return CannedAccessControlList.PublicReadWrite
				case CloudFileACL.AuthenticatedRead:
					return CannedAccessControlList.Default
				default:
					return CannedAccessControlList.Default
			}
		}

		OutputStream getOutputStream() {
			def outputStream = new PipedOutputStream()
			rawSourceStream = new PipedInputStream(outputStream)
			writeableStream = rawSourceStream
			return outputStream
		}

		void setInputStream(InputStream inputS) {
			rawSourceStream = inputS
			writeableStream = rawSourceStream
		}

		String getMetaAttribute(key) {
			if(!metaDataLoaded) {
				loadObjectMetaData()
			}
			getOSSObject().objectMetadata.userMetadata[key]
		}

		Map getMetaAttributes() {
			if(!metaDataLoaded) {
				loadObjectMetaData()
			}
			getOSSObject().objectMetadata.userMetadata
		}

		void removeMetaAttribute(key) {
			getOSSObject().objectMetadata.userMetadata.remove(key)
		}

		/**
		 * Content length metadata
		 */
		Long getContentLength() {
			if(!metaDataLoaded) {
				loadObjectMetaData()
			}
			if(internalContentLengthSet || !exists()) {
				return internalContentLength
			}
			getOSSObject().objectMetadata.contentLength
		}

		void setContentLength(Long length) {
			setMetaAttribute('Content-Length', length)
			internalContentLength = length
			internalContentLengthSet = true
		}

		/**
		 * Content type metadata
		 */
		String getContentType() {
			if(!metaDataLoaded) {
				loadObjectMetaData()
			}
			getOSSObject().objectMetadata.contentType
		}

		void setContentType(String contentType) {
			setMetaAttribute('Content-Type', contentType)
		}

		/**
		 * Bytes setter/getter
		 */
		byte[] getBytes() {
			def result = inputStream?.bytes
			inputStream?.close()
			return result
		}

		void setBytes(bytes) {
			rawSourceStream = new ByteArrayInputStream(bytes)
			writeableStream = rawSourceStream
			setContentLength(bytes.length)
		}

		/**
		 * Input stream getter
		 * @return inputStream
		 */
		InputStream getInputStream() {
			if(provider.baseUrls && provider.baseUrls[parent.name]) {
				//if we are using a custom base url to fetch it like a cloudfront edge server
				URIBuilder uriBuilder = new URIBuilder("${provider.baseUrls[parent.name]}/${encodedName}".toString())
				HttpGet request = new HttpGet(uriBuilder.build())
				HttpClientBuilder clientBuilder = HttpClients.custom()
				clientBuilder.setHostnameVerifier(new X509HostnameVerifier() {
					public boolean verify(String host, SSLSession sess) {
						return true
					}

					public void verify(String host, SSLSocket ssl) {

					}

					public void verify(String host, String[] cns, String[] subjectAlts) {

					}

					public void verify(String host, X509Certificate cert) {

					}

				})
				SSLContext sslcontext = SSLContexts.createSystemDefault()
				//ignoreSSL(sslcontext)
				SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(sslcontext) {
					@Override
					public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException, ConnectTimeoutException {
						if(socket instanceof SSLSocket) {
							try {
								socket.setEnabledProtocols(['SSLv3', 'TLSv1', 'TLSv1.1', 'TLSv1.2'] as String[])
								PropertyUtils.setProperty(socket, "host", host.getHostName());
							} catch(NoSuchMethodException ex) {
							}
							catch(IllegalAccessException ex) {
							}
							catch(InvocationTargetException ex) {
							}
						}
						return super.connectSocket(30000, socket, host, remoteAddress, localAddress, context)
					}
				}
				HttpMessageParserFactory<HttpResponse> responseParserFactory = new DefaultHttpResponseParserFactory() {

					@Override
					public HttpMessageParser<HttpResponse> create(SessionInputBuffer ibuffer, MessageConstraints constraints) {
						LineParser lineParser = new BasicLineParser() {

							@Override
							public Header parseHeader(final CharArrayBuffer buffer) {
								try {
									return super.parseHeader(buffer);
								} catch (ParseException ex) {
									return new BasicHeader(buffer.toString(), null);
								}
							}

						};
						return new DefaultHttpResponseParser(
							ibuffer, lineParser, DefaultHttpResponseFactory.INSTANCE, constraints ?: MessageConstraints.DEFAULT) {

							@Override
							protected boolean reject(final CharArrayBuffer line, int count) {
								//We need to break out of forever head reads
								if(count > 100) {
									return true
								}
								return false;

							}

						};
					}

				};
				clientBuilder.setSSLSocketFactory(sslConnectionFactory)
				Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
					.register("https", sslConnectionFactory)
					.register("http", PlainConnectionSocketFactory.INSTANCE)
					.build();

				HttpMessageWriterFactory<HttpRequest> requestWriterFactory = new DefaultHttpRequestWriterFactory();

				HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory = new ManagedHttpClientConnectionFactory(
					requestWriterFactory, responseParserFactory);
				BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(registry, connFactory)

				clientBuilder.setConnectionManager(connectionManager)

				//Proxy Settings
				if(provider.proxyHost) {
					clientBuilder.setProxy(new HttpHost(provider.proxyHost, provider.proxyPort))
					if(provider.proxyUser) {
						CredentialsProvider credsProvider = new BasicCredentialsProvider();
						NTCredentials ntCreds = new NTCredentials(provider.proxyUser, provider.proxyPassword, provider.proxyWorkstation, provider.proxyDomain)
						credsProvider.setCredentials(new AuthScope(provider.proxyHost,provider.proxyPort), ntCreds)

						clientBuilder.setDefaultCredentialsProvider(credsProvider)
						clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
					}
				}

				HttpClient client = clientBuilder.build()
				HttpResponse response = client.execute(request)
				HttpEntity entity = response.getEntity()
				return new BufferedInputStream(entity.content, 8000)
			} else {
				loadObject()
				return new BufferedInputStream(getOSSObject().objectContent, 8000)
			}
		}

		/**
		 * Text setter/getter
		 * @param encoding
		 * @return text
		 */
		String getText(String encoding = null) {
			def result
			if(encoding) {
				result = inputStream?.getText(encoding)
			} else {
				result = inputStream?.text
			}
			inputStream?.close()
			return result
		}

		void setText(String text) {
			setBytes(text.bytes)
		}

		/**
		 * Get URL or pre-signed URL if expirationDate is set
		 * @param expirationDate
		 * @return url
		 */
		URL getURL(Date expirationDate = null) {
			if(valid) {
				if(provider.baseUrl) {
					return new URL("${provider.baseUrl}/${name}")
				} else if(provider.baseUrls && provider.baseUrls[parent.name]) {
					return new URL("${provider.baseUrls[parent.name]}/${name}")
				} else if(expirationDate) {
					s3Client.generatePresignedUrl(parent.name, name, expirationDate)
				} else {
					new URL("https://${parent.name}.s3.amazonaws.com/${name}")
				}
			}
		}

		/**
		 * Check if file exists
		 * @return true or false
		 */
		Boolean exists() {
			if(valid) {
				if(existsFlag != null) {
					return existsFlag
				}
				if(!name) {
					return false
				}
				//try {
				ObjectListing objectListing = getOSSClient().listObjects(parent.name, name)
				if(objectListing.objectSummaries) {
					summary = objectListing.objectSummaries.first()
					existsFlag = true
				} else {
					existsFlag = false
				}
				//} catch (AmazonS3Exception exception) {
				//log.warn(exception)
				//} catch (AmazonClientException exception) {
				//log.warn(exception)
				//}
				existsFlag
			} else {
				false
			}
		}

		/**
		 * Save file
		 */
		def save(acl) {
			if(valid) {
				assert writeableStream
				setMetaAttribute('Object-Acl', acl)

				Long contentLength = (internalContentLengthSet || !exists()) ? internalContentLength : getOSSObject().objectMetadata.contentLength
				if(contentLength != null && contentLength <= 4 * 1024l * 1024l * 1024l && parent.provider.forceMultipart == false) {
					PutObjectRequest putObjectRequest = new PutObjectRequest(parent.name,name,writeableStream,getOSSObject().objectMetadata)
					getOSSClient().putObject(putObjectRequest)
				} else if(contentLength != null) {
					saveChunked()
				} else {
					File tmpFile = cacheStreamToFile(null,rawSourceStream)
					this.setContentLength(tmpFile.size())
					InputStream is = tmpFile.newInputStream()
					try {
						this.setInputStream(is)
						return this.save(acl)
					} finally {
						if(is) {
							try { is.close()} catch(ex) {}
						}
						cleanupCacheStream(tmpFile)
					}
				}
				object = null
				summary = null
				existsFlag = true
			}
		}

		def saveChunked() {
			Long contentLength = getOSSObject().objectMetadata.contentLength
			List<PartETag> partETags = new ArrayList<PartETag>();
			InitiateMultipartUploadRequest initRequest = new
				InitiateMultipartUploadRequest(parent.name, name);
			initRequest.setObjectMetadata(getOSSObject().objectMetadata)
			getOSSClient().initiateMultipartUpload(initRequest)
			InitiateMultipartUploadResult initResponse =
				s3Client.initiateMultipartUpload(initRequest);
			long partSize = parent.provider.chunkSize; // Set part size to 5 MB.

			if(contentLength && contentLength / 1000l > partSize) {
				partSize = contentLength / 1000l + 1l
			}
			ChunkedInputStream chunkedStream = new ChunkedInputStream(rawSourceStream, partSize)

			long filePosition = 0
			int partNumber = 1
			while(chunkedStream.available() >= 0 && (!contentLength || filePosition < contentLength)) {
				// Last part can be less than 5 MB. Adjust part size.
				if(contentLength) {
					partSize = Math.min(partSize, (contentLength - filePosition));

					// Create request to upload a part.

					UploadPartRequest uploadRequest = new UploadPartRequest()
					uploadRequest.setUploadId(initResponse.getUploadId())
					uploadRequest.setBucketName(parent.name)
					uploadRequest.setKey(name)
					uploadRequest.setPartNumber(partNumber)
					uploadRequest.setPartSize(partSize)
					uploadRequest.setInputStream(chunkedStream)

					// Upload part and add response to our list.
					partETags.add(getOSSClient().uploadPart(uploadRequest).getPartETag())
				} else {
					byte[] buff = new byte[partSize]
					def lessThan = false
					int count = chunkedStream.read(buff)
					if(count <= 0) {
						break
					} else if(count < partSize) {
						lessThan = true
					}

					partSize = count
					// Create request to upload a part.
					UploadPartRequest uploadRequest = new UploadPartRequest()
					uploadRequest.setUploadId(initResponse.getUploadId())
					uploadRequest.setBucketName(parent.name)
					uploadRequest.setKey(name)
					uploadRequest.setPartNumber(partNumber)
					uploadRequest.setPartSize(partSize)
					uploadRequest.setInputStream(new ByteArrayInputStream(buff, 0, partSize))

					// Upload part and add response to our list.
					partETags.add(getOSSClient().uploadPart(uploadRequest).getPartETag());
					if(lessThan) {
						break
					}
				}


				filePosition += partSize;
				partNumber++
				chunkedStream.nextChunk()
			}
			// Step 3: Complete.
			CompleteMultipartUploadRequest compRequest = new
				CompleteMultipartUploadRequest(
				parent.name,
				name,
				initResponse.getUploadId(),
				partETags);

			getOSSClient().completeMultipartUpload(compRequest);
		}

		/**
		 * Delete file
		 */
		def delete() {
			if(valid) {
				getOSSClient().deleteObject(parent.name, name)
				existsFlag = false
			}
		}

		// PRIVATE

		private OSSClient getOSSClient() {
			((AlibabaStorageProvider)parent.provider).getOSSClient()
		}

		private OSSObject getOSSObject() {
			if(!object) {
				object = new OSSObject(bucketName: parent.name, key: name)
				loaded = false
			}
			object
		}

		private void loadObject() {
			if(valid) {
				object = getOSSClient().getObject(parent.name, name)
				loaded = true
				metaDataLoaded = false
			}
		}

		private void loadObjectMetaData() {
			if(valid) {
				getOSSObject().objectMetadata = getOSSClient().getObjectMetadata(parent.name, name)
				metaDataLoaded = true
			}
		}

		private boolean isValid() {
			assert parent
			assert parent.name
			assert name
			true
		}

		private String getEncodedName() {
			return java.net.URLEncoder.encode(name, "UTF-8").replaceAll('\\+', '%20')
		}

		private ignoreSSL(SSLContext sslContext) {
			TrustManager[] trustAllCerts = [
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(
						java.security.cert.X509Certificate[] certs, String authType) {
					}

					public void checkServerTrusted(
						java.security.cert.X509Certificate[] certs, String authType) {
					}
				}
			] as TrustManager[]
			// set up a TrustManager that trusts everything
			sslContext.init(null, trustAllCerts, new SecureRandom());
		}

}
