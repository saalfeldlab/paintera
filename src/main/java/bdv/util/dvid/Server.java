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
	
	public String getUrl()
	{
		return url;
	}
	
	public String getApiUrl()
	{
		return apiUrl;
	}
	
	public JsonObject getReposInfo() throws JsonSyntaxException, JsonIOException, IOException
	{
		String infoUrl = this.apiUrl + "/repos/info";
		JsonObject json = JsonHelper.fetch( DvidUrlOptions.getRequestString( infoUrl ), JsonObject.class );
		return json;
	}
	
	public JsonObject getRepoInfoFromUuid( String uuid ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject json = this.getReposInfo();
		for ( Entry< String, JsonElement > entry : json.entrySet() )
			if ( Node.uuidEquivalenceCheck( entry.getKey(), uuid ) )
				return entry.getValue().getAsJsonObject();
		return null;
	}
	
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
	
	public Repository createRepo( String alias ) throws MalformedURLException, IOException
	{
		return createRepo( alias, "" );
	}
	
	public Repository createRepo( String alias, String description ) throws MalformedURLException, IOException
	{
		JsonObject info = new JsonObject();
		info.addProperty( "alias", alias );
		info.addProperty( "description", description );
		return createRepo( info );
	}
	
	public Repository createRepo( JsonObject info ) throws MalformedURLException, IOException
	{
		String postUrl = getApiUrl() + "/repos";
		HttpURLConnection connection = HttpRequest.postRequestJSON( DvidUrlOptions.getRequestString( postUrl ), info );
		JsonObject response = new Gson().fromJson( new InputStreamReader( connection.getInputStream() ), JsonObject.class );
		connection.disconnect();
		String uuid = response.get( "root" ).getAsString();
		return new Repository( this, uuid );
	}
	
	public Repository getRepoFromUuid( String uuid ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject info = getRepoInfoFromUuid( uuid );
		if ( info == null )
			throw new RuntimeException( "Repo " + uuid + " not found in server " + this.url );
		return new Repository( this, uuid );
	}
	
	public Repository getRepo( String alias ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject repoInfo = getRepoInfo( alias );
		if ( repoInfo == null )
			throw new RuntimeException( "Repo " + alias + " not found in server " + this.url );
		return new Repository( this, repoInfo.get( Repository.RepositoryInfo.ROOT_KEY ).getAsString() );
	}
	
	public int deleteRepo( String uuid ) throws IOException
	{
		// /api/repo/{uuid}?imsure=true
		HashMap< String, String > deleteOptions = new HashMap< String, String >();
		deleteOptions.put( "imsure", "true" );
		String url = DvidUrlOptions.getRequestString( this.getApiUrl() + "/repo/" + uuid, "", deleteOptions	);
		return HttpRequest.delete( url );
	}
	
	public int deleteRepo( Repository repo ) throws IOException
	{
		return deleteRepo( repo.getUuid() );
	}
	
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
