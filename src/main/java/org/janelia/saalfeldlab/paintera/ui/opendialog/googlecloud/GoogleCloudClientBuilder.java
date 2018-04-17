package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth.Scope;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;

import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;

public class GoogleCloudClientBuilder
{
	public static Storage createStorage( final Supplier< InputStream > clientSecretsIfNotFound ) throws IOException
	{
		return createStorage( createOAuth( clientSecretsIfNotFound ), null );
	}

	public static Storage createStorage( final String projectId, final Supplier< InputStream > clientSecretsIfNotFound ) throws IOException
	{
		return createStorage( createOAuth( clientSecretsIfNotFound ), projectId );
	}

	public static Storage createStorage( final GoogleCloudOAuth oauth ) throws IOException
	{
		return createStorage( oauth, null );
	}

	public static Storage createStorage( final GoogleCloudOAuth oauth, final String projectId )
	{
		final GoogleCloudStorageClient storageClient = new GoogleCloudStorageClient(
				oauth.getAccessToken(),
				oauth.getClientSecrets(),
				oauth.getRefreshToken() );
		return projectId == null ? storageClient.create() : storageClient.create( projectId );
	}

	public static ResourceManager createResourceManager( final Supplier< InputStream > clientSecretsIfNotFound ) throws IOException
	{
		return createResourceManager( createOAuth( clientSecretsIfNotFound ) );
	}

	public static ResourceManager createResourceManager( final GoogleCloudOAuth oauth )
	{
		return new GoogleCloudResourceManagerClient(
				oauth.getAccessToken(),
				oauth.getClientSecrets(),
				oauth.getRefreshToken() ).create();
	}

	public static GoogleCloudOAuth createOAuth( final Supplier< InputStream > clientSecretsIfNotFound ) throws IOException
	{
		return createOAuth( createScopes(), clientSecretsIfNotFound );
	}

	public static GoogleCloudOAuth createOAuth( final Collection< Scope > scopes, final Supplier< InputStream > clientSecretsIfNotFound ) throws IOException
	{
		return new GoogleCloudOAuth(
				scopes,
				"n5-viewer-google-cloud-oauth2",
				Optional.ofNullable( GoogleCloudClientBuilder.class.getResourceAsStream( "/googlecloud_client_secrets.json" ) ).orElse( clientSecretsIfNotFound.get() ) );
	}

	private static Collection< Scope > createScopes()
	{
		return Arrays.asList(
				GoogleCloudResourceManagerClient.ProjectsScope.READ_ONLY,
				GoogleCloudStorageClient.StorageScope.READ_WRITE );
	}
}
