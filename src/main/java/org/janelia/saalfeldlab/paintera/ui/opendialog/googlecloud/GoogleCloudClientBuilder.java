package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

public class GoogleCloudClientBuilder
{

	public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static Storage createStorage() throws Exception
	{
		return createStorage(null);
	}

	public static Storage createStorage(final String projectId) throws Exception
	{
		final Storage client = new GoogleCloudStorageClient(projectId).create();
		if (!verifyCredentials())
			throw new Exception();
		return client;
	}

	public static ResourceManager createResourceManager() throws Exception
	{
		final ResourceManager client = new GoogleCloudResourceManagerClient().create();
		if (!verifyCredentials())
			throw new Exception();
		return client;
	}

	private static boolean verifyCredentials() throws IOException
	{
		return GoogleCredentials.getApplicationDefault() != null;
	}
}
