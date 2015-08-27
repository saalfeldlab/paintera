package bdv.util.dvid;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map.Entry;

import bdv.util.JsonHelper;
import bdv.util.http.HttpRequest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class Server
{
	
	private final String url;
	private final String apiUrl;

	public Server( String url )
	{
		super();
		this.url = url;
		this.apiUrl = url + "/api";
	}
	
	public Server( String hostname, String port )
	{
		this( hostname + ":" + port );
	}
	
	public Server( String hostname, int port )
	{
		this( hostname, Integer.toString( port ) );
	}
	
	/**
	 * @return Url to this server, including port: <hostname>:<port>
	 */
	public String getUrl()
	{
		return url;
	}
	
	/**
	 * @return Url to api of this sever: <url>/api
	 */
	public String getApiUrl()
	{
		return apiUrl;
	}
	
	/**
	 * Get info about repositories on this sever.
	 * @return {@link JsonObject} holding information about repositories.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public JsonObject getReposInfo() throws JsonSyntaxException, JsonIOException, IOException
	{
		String infoUrl = this.apiUrl + "/repos/info";
		JsonObject json = JsonHelper.fetch( DvidUrlOptions.getRequestString( infoUrl ), JsonObject.class );
		return json;
	}
	
	/**
	 * 
	 * Get info about a repository by uuid identifier.
	 * 
	 * @param uuid Repository identifier.
	 * @return {@link JsonObject} holding information about repository identified by uuid,
	 * if uuid is found, null otherwise.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public JsonObject getRepoInfoFromUuid( String uuid ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject json = this.getReposInfo();
		for ( Entry< String, JsonElement > entry : json.entrySet() )
			if ( Node.uuidEquivalenceCheck( entry.getKey(), uuid ) )
				return entry.getValue().getAsJsonObject();
		return null;
	}
	
	/**
	 * 
	 * Get info about a repository by alias. The alias need not be unique. The first
	 * match is returned (or null if no match).
	 * 
	 * @param alias Repository alias.
	 * @return {@link JsonObject} holding information about repository identified by alias,
	 * if uuid is found, null otherwise.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public JsonObject getRepoInfo( String alias ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject json = this.getReposInfo();
		for ( Entry< String, JsonElement > entry : json.entrySet() )
		{
			JsonObject v = entry.getValue().getAsJsonObject();
			String repoAlias = v.get( Repository.RepositoryInfo.ALIAS_KEY ).getAsString();
			if ( repoAlias.compareTo( alias  ) == 0 )
				return v;
		}
		return null;
	}
	
	/**
	 * Create repository with alias.
	 * 
	 * @param alias Alias of repository.
	 * @return {@link Repository} representing the new repository.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public Repository createRepo( String alias ) throws MalformedURLException, IOException
	{
		return createRepo( alias, "" );
	}
	
	/**
	 * Create repository with alias and description.
	 * 
	 * @param alias Alias of repository.
	 * @param description Description of the repository.
	 * @return {@link Repository} representing the new repository.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public Repository createRepo( String alias, String description ) throws MalformedURLException, IOException
	{
		JsonObject info = new JsonObject();
		info.addProperty( "alias", alias );
		info.addProperty( "description", description );
		return createRepo( info );
	}
	
	/**
	 * Create repository with alias and description.
	 * 
	 * @param info {@link JsonObject} holding alias and description.
	 * @return {@link Repository} representing the new repository.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public Repository createRepo( JsonObject info ) throws MalformedURLException, IOException
	{
		String postUrl = getApiUrl() + "/repos";
		HttpURLConnection connection = HttpRequest.postRequestJSON( DvidUrlOptions.getRequestString( postUrl ), info );
		JsonObject response = new Gson().fromJson( new InputStreamReader( connection.getInputStream() ), JsonObject.class );
		connection.disconnect();
		String uuid = response.get( "root" ).getAsString();
		return new Repository( this, uuid );
	}
	
	
	/**
	 * Create {@link Repository} instance for existing repository. 
	 * 
	 * @param uuid Identifier of repository.
	 * @return {@link Repository} representation of repository with uuid if uuid found,
	 * null otherwise.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public Repository getRepoFromUuid( String uuid ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject info = getRepoInfoFromUuid( uuid );
		if ( info == null )
			return null;
		return new Repository( this, uuid );
	}
	
	/**
	 * Create {@link Repository} instance for existing repository. 
	 * 
	 * @param alias Alias of repository. The alias need not be unique. The first
	 * match is returned (or null if no match).
	 * @return {@link Repository} representation of repository with uuid if uuid found,
	 * null otherwise.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public Repository getRepo( String alias ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject repoInfo = getRepoInfo( alias );
		if ( repoInfo == null )
			return null;
		return new Repository( this, repoInfo.get( Repository.RepositoryInfo.ROOT_KEY ).getAsString() );
	}
	
	/**
	 * 
	 * Remove repository from dvid server represented by this.
	 * 
	 * @param uuid Identifier of repository.
	 * @return HTTP Status code of DELETE request.
	 * @throws IOException
	 */
	public int deleteRepo( String uuid ) throws IOException
	{
		// /api/repo/{uuid}?imsure=true
		HashMap< String, String > deleteOptions = new HashMap< String, String >();
		deleteOptions.put( "imsure", "true" );
		String url = DvidUrlOptions.getRequestString( this.getApiUrl() + "/repo/" + uuid, "", deleteOptions	);
		return HttpRequest.delete( url );
	}
	
	/**
	 * 
	 * Remove repository from dvid server represented by this.
	 * 
	 * @param repo {@link Repository} representation of dvid repository.
	 * @return HTTP Status code of DELETE request.
	 * @throws IOException
	 */
	public int deleteRepo( Repository repo ) throws IOException
	{
		return deleteRepo( repo.getUuid() );
	}
	
	/**
	 * 
	 * Remove repository from dvid server represented by this.
	 * 
	 * @param node {@link Node} representing that is part of dvid repository
	 * to be deleted. 
	 * @return HTTP Status code of DELETE request.
	 * @throws IOException
	 */
	public int deleteRepo( Node node ) throws IOException
	{
		return deleteRepo( node.getUuid() );
	}
	
	public static void main( String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		String url = "http://vm570.int.janelia.org:8080";
		Server s = new Server( url );
		System.out.println( s.getReposInfo().toString() );
		System.out.println( s.getRepoInfo( "snemi-superpixels" ) );
		System.out.println( s.getRepo( "snemi-superpixels" ).getInfo() );
//		Repository r = s.createRepo( "abc" );
//		Node root = r.getRootNode();
//		root.commit( "testing", new String[] {} );
//		Node child = root.branch( "some branch" );
//		child.commit( "some more testing", new String[] {}  );
//		System.out.println( s.getReposInfo().toString() );
	}
	
}
