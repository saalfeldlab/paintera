package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth.Scope;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;

public class GoogleCloudClientBuilder
{

	public static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final String USER_HOME = System.getProperty( "user.home" );

	public static final String CLIENT_SECRETS_FILE = Paths.get( USER_HOME, ".google", "n5-google-cloud" ).toString();

	public static Storage createStorage( final Supplier< InputStream > clientSecretsIfNotFound ) throws Exception
	{
		return createStorage( createOAuth( clientSecretsIfNotFound ), null );
	}

	public static Storage createStorage( final String projectId, final Supplier< InputStream > clientSecretsIfNotFound ) throws Exception
	{
		return createStorage( createOAuth( clientSecretsIfNotFound ), projectId );
	}

	public static Storage createStorage( final GoogleCloudOAuth oauth ) throws Exception
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

	public static ResourceManager createResourceManager( final Supplier< InputStream > clientSecretsIfNotFound ) throws Exception
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

	public static GoogleCloudOAuth createOAuth( final Supplier< InputStream > clientSecretsIfNotFound ) throws Exception
	{
		return createOAuth( createScopes(), clientSecretsIfNotFound );
	}

	public static GoogleCloudOAuth createOAuth( final Collection< Scope > scopes, final Supplier< InputStream > clientSecretsIfNotFound ) throws Exception
	{
		final InputStream stream = Optional
				.of( CLIENT_SECRETS_FILE )
				.map( MakeUnchecked.orElse( FileInputStream::new, fn -> ( InputStream ) null ) )
				.orElseGet( clientSecretsIfNotFound::get );

		final GoogleCloudOAuth[] oauth = { null };
		final CountDownLatch latch = new CountDownLatch( 1 );
		new Thread( MakeUnchecked.unchecked( () -> {
			Thread.currentThread().setName( "google-cloud-oauth" );
			oauth[ 0 ] = new GoogleCloudOAuth(
					scopes,
					"n5-viewer-google-cloud-oauth2",
					stream );
			latch.countDown();
		} )
				).start();
		latch.await();
		if ( oauth[ 0 ] != null )
		{
			LOG.debug( "Created oauth with secrets={}", oauth[ 0 ].getClientSecrets() );
		}
		return oauth[ 0 ];
	}

	private static Collection< Scope > createScopes()
	{
		return Arrays.asList(
				GoogleCloudResourceManagerClient.ProjectsScope.READ_ONLY,
				GoogleCloudStorageClient.StorageScope.READ_WRITE );
	}
}
