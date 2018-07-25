package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudClient.Scope;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudClientSecretsPrompt;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCloudClientBuilder
{

	public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String USER_HOME = System.getProperty("user.home");

	public static Storage createStorage(final Supplier<GoogleClientSecrets> clientSecretsIfNotFound) throws Exception
	{
		return createStorage(createOAuth(clientSecretsIfNotFound), null);
	}

	public static Storage createStorage(final String projectId, final Supplier<GoogleClientSecrets>
			clientSecretsIfNotFound)
	throws Exception
	{
		return createStorage(createOAuth(clientSecretsIfNotFound), projectId);
	}

	public static Storage createStorage(final GoogleCloudOAuth oauth) throws Exception
	{
		return createStorage(oauth, null);
	}

	public static Storage createStorage(final GoogleCloudOAuth oauth, final String projectId)
	{
		final GoogleCloudStorageClient storageClient = new GoogleCloudStorageClient(
				oauth.getCredentials(), projectId);
		return storageClient.create();
	}

	public static ResourceManager createResourceManager(final Supplier<GoogleClientSecrets> clientSecretsIfNotFound)
	throws Exception
	{
		return createResourceManager(createOAuth(clientSecretsIfNotFound));
	}

	public static ResourceManager createResourceManager(final GoogleCloudOAuth oauth)
	{
		return new GoogleCloudResourceManagerClient(oauth.getCredentials()).create();
	}

	public static GoogleCloudOAuth createOAuth(final Supplier<GoogleClientSecrets> clientSecretsIfNotFound)
	throws Exception
	{
		return createOAuth(createScopes(), clientSecretsIfNotFound);
	}

	public static GoogleCloudOAuth createOAuth(final Collection<Scope> scopes, final Supplier<GoogleClientSecrets>
			clientSecretsIfNotFound)
	throws Exception
	{
		final GoogleCloudClientSecretsPrompt prompt = new GoogleCloudClientSecretsPrompt()
		{

			@Override
			public GoogleClientSecrets prompt(final GoogleCloudClientSecretsPromptReason reason)
			throws GoogleCloudSecretsPromptCanceledException
			{
				return clientSecretsIfNotFound.get();
			}
		};

		return new GoogleCloudOAuth(scopes, prompt, new CredentialProviderWithWebView());
	}

	private static Collection<Scope> createScopes()
	{
		return Arrays.asList(
				GoogleCloudResourceManagerClient.ProjectsScope.READ_ONLY,
				GoogleCloudStorageClient.StorageScope.READ_WRITE
		                    );
	}
}
