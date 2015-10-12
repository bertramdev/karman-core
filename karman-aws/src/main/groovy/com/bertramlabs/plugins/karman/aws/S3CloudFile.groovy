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

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.Headers
import com.amazonaws.services.s3.model.*
import com.bertramlabs.plugins.karman.CloudFile

class S3CloudFile extends CloudFile {

    S3Directory parent
	S3Object object
    S3ObjectSummary summary // Only set when object is retrieved by listFiles

    private Boolean loaded = false
	private Boolean metaDataLoaded = false
    private Boolean existsFlag = null

    /**
     * Meta attributes setter/getter
     */
    void setMetaAttribute(key, value) {
        switch(key) {
            case Headers.CACHE_CONTROL:
                s3Object.objectMetadata.cacheControl = value
                break
            case Headers.CONTENT_DISPOSITION:
                s3Object.objectMetadata.contentDisposition = value
                break
            case Headers.CONTENT_ENCODING:
                s3Object.objectMetadata.contentEncoding = value
                break
            case Headers.CONTENT_LENGTH:
                s3Object.objectMetadata.contentLength = value
                break
            case Headers.CONTENT_MD5:
                s3Object.objectMetadata.contentMD5 = value
                break
            case Headers.CONTENT_TYPE:
                s3Object.objectMetadata.contentType = value
                break
            case Headers.EXPIRES:
                s3Object.objectMetadata.httpExpiresDate = value
                break
            case Headers.S3_CANNED_ACL:
                s3Object.objectMetadata.setHeader(Headers.S3_CANNED_ACL, value)
                break
            default:
                // User specific meta
                s3Object.objectMetadata.userMetadata[key] = value
        }
    }

    OutputStream getOutputStream() {
        def outputStream = new PipedOutputStream()

        s3Object.objectContent = new S3ObjectInputStream(new PipedInputStream(outputStream), null)
        return outputStream
    }

    void setInputStream(InputStream inputS) {
        s3Object.objectContent = new S3ObjectInputStream(new PipedInputStream(inputS), null)
    }

    String getMetaAttribute(key) {
        if (!metaDataLoaded) {
            loadObjectMetaData()
        }
        s3Object.objectMetadata.userMetadata[key]
    }
    Map getMetaAttributes() {
        if (!metaDataLoaded) {
            loadObjectMetaData()
        }
        s3Object.objectMetadata.userMetadata
    }
    void removeMetaAttribute(key) {
        s3Object.objectMetadata.userMetadata.remove(key)
    }

    /**
     * Content length metadata
     */
    Long getContentLength() {
        if (!metaDataLoaded) {
            loadObjectMetaData()
        }
        s3Object.objectMetadata.contentLength
    }
    void setContentLength(Long length) {
        setMetaAttribute(Headers.CONTENT_LENGTH, length)
    }

    /**
     * Content type metadata
     */
    String getContentType() {
        if (!metaDataLoaded) {
            loadObjectMetaData()
        }
        s3Object.objectMetadata.contentType
    }
    void setContentType(String contentType) {
        setMetaAttribute(Headers.CONTENT_TYPE, contentType)
    }

    /**
     * Bytes setter/getter
     */
    byte[] getBytes() {
        def result = inputStream?.bytes
        inputStream.close()
        return result
    }
    void setBytes(bytes) {
        s3Object.objectContent = new S3ObjectInputStream(new ByteArrayInputStream(bytes), null)
        setContentLength(bytes.length)
    }

    /**
     * Input stream getter
     * @return inputStream
     */
    InputStream getInputStream() {
        if (!object) {
            loadObject()
        }
		s3Object.objectContent
	}

    /**
     * Text setter/getter
     * @param encoding
     * @return text
     */
	String getText(String encoding = null) {
		def result
		if (encoding) {
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
        if (valid) {
            if (expirationDate) {
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
        if (valid) {
            if (existsFlag != null) {
                return existsFlag
            }
            if (!name) {
                return false
            }
            //try {
                ObjectListing objectListing = s3Client.listObjects(parent.name, name)
                if (objectListing.objectSummaries) {
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
        if (valid) {
            assert inputStream
            setMetaAttribute(Headers.S3_CANNED_ACL, acl)

            s3Client.putObject(parent.name, name, inputStream, object.objectMetadata)
            object = null
            summary = null
            existsFlag = true
        }
	}

    /**
     * Delete file
     */
	def delete() {
        if (valid) {
            s3Client.deleteObject(parent.name, name)
            existsFlag = false
        }
	}

    // PRIVATE

    private AmazonS3Client getS3Client() {
        parent.provider.s3Client
    }

    private S3Object getS3Object() {
        if (!object) {
            object = new S3Object(bucketName: parent.name, key: name)
            loaded = false
        }
        object
    }

    private void loadObject() {
        if (valid) {
            object = s3Client.getObject(parent.name, name)
            loaded = true
            metaDataLoaded = false
        }
    }

    private void loadObjectMetaData() {
        if (valid) {
            s3Object.objectMetadata = s3Client.getObjectMetadata(parent.name, name)
            metaDataLoaded = true
        }
    }

    private boolean isValid() {
        assert parent
        assert parent.name
        assert name
        true
    }

}
