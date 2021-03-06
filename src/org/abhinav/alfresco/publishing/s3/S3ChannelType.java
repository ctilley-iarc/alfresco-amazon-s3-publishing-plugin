/*
 * Created By: Abhinav Kumar Mishra
 * Copyright &copy; 2014. Abhinav Kumar Mishra. 
 * All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.abhinav.alfresco.publishing.s3;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.repo.publishing.AbstractChannelType;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jets3t.service.ServiceException;

import com.abhinav.alfresco.publishing.utils.S3RESTService;

/**
 * The S3ChannelType <br/>
 * Channel definition for publishing/unpublishing objects to Amazon S3.<br/>
 * 
 * @author Abhinav Kumar Mishra
 */
public class S3ChannelType extends AbstractChannelType {
    
    /** The Constant log. */
    private final static Log LOG = LogFactory.getLog(S3ChannelType.class);

    /** The Constant ID. */
    public final static String ID = "s3";
     
    /** The Constant DEFAULT_SUPPORTED_MIME_TYPES. */
 	private final static Set<String> DEFAULT_SUPPORTED_MIME_TYPES = S3PublishingHelper.getMimeTypesToBeSupported();
 	
    /** The publishing helper. */
    private S3PublishingHelper publishingHelper;
    
    /** The content service. */
    private ContentService contentService;

    /** The supported mime types. */
    private Set<String> supportedMimeTypes = DEFAULT_SUPPORTED_MIME_TYPES;

    /**
     * Sets the supported mime types.
     *
     * @param mimeTypes the new supported mime types
     */
    public void setSupportedMimeTypes(final Set<String> mimeTypes) {
        supportedMimeTypes = Collections.unmodifiableSet(new TreeSet<String>(mimeTypes));
    }

    /**
     * Sets the publishing helper.
     *
     * @param s3PublishingHelper the new publishing helper
     */
	public void setPublishingHelper(final S3PublishingHelper s3PublishingHelper) {
		this.publishingHelper = s3PublishingHelper;
	}

    /**
     * Sets the content service.
     *
     * @param contentService the new content service
     */
	public void setContentService(final ContentService contentService) {
		this.contentService = contentService;
	}

    /* (non-Javadoc)
     * @see org.alfresco.service.cmr.publishing.channels.ChannelType#canPublish()
     */
	public boolean canPublish() {
		return true;
	}

    /* (non-Javadoc)
     * @see org.alfresco.service.cmr.publishing.channels.ChannelType#canPublishStatusUpdates()
     */
	public boolean canPublishStatusUpdates() {
		return false;
	}

    /* (non-Javadoc)
     * @see org.alfresco.service.cmr.publishing.channels.ChannelType#canUnpublish()
     */
	public boolean canUnpublish() {
		return true;
	}

    /* (non-Javadoc)
     * @see org.alfresco.service.cmr.publishing.channels.ChannelType#getChannelNodeType()
     */
	public QName getChannelNodeType() {
		return S3PublishingModel.TYPE_DELIVERY_CHANNEL;
	}

    /* (non-Javadoc)
     * @see org.alfresco.service.cmr.publishing.channels.ChannelType#getId()
     */
	public String getId() {
		return ID;
	}

    /* (non-Javadoc)
     * @see org.alfresco.repo.publishing.AbstractChannelType#getSupportedMimeTypes()
     */
    @Override
	public Set<String> getSupportedMimeTypes() {
		return supportedMimeTypes;
	}

    /* (non-Javadoc)
     * @see org.alfresco.repo.publishing.AbstractChannelType#publish(org.alfresco.service.cmr.repository.NodeRef, java.util.Map)
     */
    @Override
	public void publish(final NodeRef nodeToPublish,
			final Map<QName, Serializable> channelProperties) {
		LOG.info("publish() invoked...");

		final ContentReader contentReader = contentService.getReader(nodeToPublish,ContentModel.PROP_CONTENT);
		if (contentReader.exists()) {
			File contentFile=null;
			boolean deleteContentFrmTempFlag = false;
			if (FileContentReader.class.isAssignableFrom(contentReader.getClass())) {
				contentFile = ((FileContentReader) contentReader).getFile();
			} else {
				// Otherwise clone the content to temp and use it
				final File tempDir = TempFileProvider.getLongLifeTempDir(ID);
				contentFile = TempFileProvider.createTempFile(ID, "", tempDir);
				contentReader.getContent(contentFile);
				deleteContentFrmTempFlag = true;
			}

			S3RESTService s3RestServ = null;
			try {
				final String mimeType = contentReader.getMimetype();
				final String nodeUri = nodeToPublish.toString();
				LOG.info("Publishing object: " + nodeUri);
				LOG.info("Content_MIMETYPE: " + mimeType);
				s3RestServ = publishingHelper.getS3Service(channelProperties);			
				s3RestServ.putObject(nodeUri,S3PublishingHelper.getBytes(contentFile));
			} catch (NoSuchAlgorithmException | ServiceException excp) {
				LOG.error("Exception in Unpublish(): ", excp);
				throw new AlfrescoRuntimeException(excp.getLocalizedMessage());
			} catch (IllegalStateException illegalEx) {
				LOG.error("Exception in publish(): ", illegalEx);
				throw new AlfrescoRuntimeException(illegalEx.getLocalizedMessage());
			} catch (IOException ioex) {
				LOG.error("Exception in publish(): ", ioex);
				throw new AlfrescoRuntimeException(ioex.getLocalizedMessage());
			} finally {
				try {
					if(s3RestServ!=null){
					  s3RestServ.shutDownS3Service();
					}
				} catch (ServiceException servExcp) {
					LOG.error("Exception in publish() while shutting down s3 service: ",servExcp);
				}
				if (deleteContentFrmTempFlag) {
					contentFile.delete();
				}
			}
		}
	}

    /* (non-Javadoc)
     * @see org.alfresco.repo.publishing.AbstractChannelType#unpublish(org.alfresco.service.cmr.repository.NodeRef, java.util.Map)
     */
	@Override
	public void unpublish(final NodeRef nodeToUnpublish,
			final Map<QName, Serializable> channelProperties) {

		LOG.info("unpublish() invoked...");
		S3RESTService s3RestServ = null;
		try {
			LOG.debug("Unpublishing object: " + nodeToUnpublish);
			s3RestServ = publishingHelper.getS3Service(channelProperties);
			s3RestServ.deleteObject(nodeToUnpublish.toString());
		} catch (NoSuchAlgorithmException | ServiceException excp) {
			LOG.error("Exception in Unpublish(): ", excp);
			throw new AlfrescoRuntimeException(excp.getLocalizedMessage());
		} catch (IllegalStateException illegalEx) {
			LOG.error("Exception in Unpublish(): ", illegalEx);
			throw new AlfrescoRuntimeException(illegalEx.getLocalizedMessage());
		} catch (IOException ioex) {
			LOG.error("Exception in Unpublish(): ", ioex);
			throw new AlfrescoRuntimeException(ioex.getLocalizedMessage());
		} finally {
			try {
				if(s3RestServ!=null){
				  s3RestServ.shutDownS3Service();
				}
			} catch (ServiceException servExcp) {
				LOG.error("Exception in Unpublish() while shutting down s3 service: ",servExcp);
			}
		}
	}
}
