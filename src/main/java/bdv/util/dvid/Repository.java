package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map.Entry;

import bdv.util.JsonHelper;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class Repository
{
	
	public static class RepositoryInfo
	{
		public static String ALIAS_KEY="Alias";
		public static String ROOT_KEY="Root";
		public static String LOG_KEY="Log";
		public static String PROPERTIES_KEY="Properties";
		public static String DATA_INSTANCES_KEY="DataInstances";
		public static String DAG_KEY="DAG";
		
	}
	
	
	
	private final String uuid;
	private final Server server;
	
	
	public Repository( String url, String uuid )
	{
		this( new Server(url), uuid );
	}
	
	public Repository( Server server, String uuid )
	{
		this.server = server;
		this.uuid = uuid;
	}

	public Server getServer()
	{
		return this.server;
	}
	
	public String getUuid()
	{
		return uuid;
	}
	
	public String getUrl()
	{
		return server.getApiUrl() + "/repo/" + this.uuid;
	}
	
	public String getNodeUrl()
	{
		return server.getApiUrl() +"/node/" + this.uuid;
	}
	
	public Node getRootNode()
	{
		return new Node( this.uuid, this );
	}
	
	public Node checkout( String uuid ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject dag = getDAG().get( "Nodes" ).getAsJsonObject();
		for ( Entry< String, JsonElement > node : dag.entrySet() )
			if ( Node.uuidEquivalenceCheck( node.getKey(), uuid ) )
				return new Node( uuid, this );
		return null;
	}
	
	public JsonObject getInfo() throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject infoObject = JsonHelper.fetch( this.server.getApiUrl() + "/repo/" + this.uuid + "/info", JsonObject.class );
		return infoObject;
	}
	
	public JsonObject getDAG() throws JsonSyntaxException, JsonIOException, IOException
	{
		return getInfo().get( "DAG" ).getAsJsonObject();
	}
	
	public Dataset createDataset( String name, String type, String... sync ) throws MalformedURLException, IOException
	{
		return this.getRootNode().createDataset( name, type, sync );
		
	}
	
	public static JsonObject generateFromAliasAndDescription( String alias, String description )
	{
		JsonObject json = new JsonObject();
		json.addProperty( "alias", alias );
		json.addProperty( "description", description );
		return json;
	}
	
	public static void main( String[] args ) throws MalformedURLException, IOException
	{
		
		String url = "http://vm570.int.janelia.org:8080";
		String uuid = "2fa87e8e61684bef9d2a92756a65d228";
		Repository repo = new Repository( url, uuid );
		
		// To create new repo, do: 
		// Repository repo = new Repository( apiUrl, uuid );
		
		System.out.println( repo.getServer().getApiUrl() );
		System.out.println( repo.getUuid() );
		
//		Dataset ds = repo.create( "testing123456", "labelblk" );
		Dataset ds = new Dataset( repo.getRootNode(), "testing123456" );
		
		System.out.println( ds.getName() );
		System.out.println( ds.getNode().getUuid() );
		System.out.println( ds.getInfo().toString() );
		
		System.out.println();
		Repository r2 = new Repository( repo.getServer(), "6efb517b5ca64b67b8d53be310a9bca4" );
		Node n2 = new Node( "6efb517b5ca64b67b8d53be310a9bca4", repo );
		Dataset d2 = new Dataset( n2, "some-data-set" );
		System.out.println( repo.getInfo() );
//		System.out.println( repo.getDAG().toString() );
		
//		repo.getRootNode().commit( "bla", new String[0] );
//		Node newNode = repo.getRootNode().branch( "bleb" );
//		Dataset newDs = newNode.createDataset( "some-data-set", "labelblk" );
//		System.out.println( newDs.getInfo().toString() );
//		System.out.println( repo.getInfo() );
		
//		Node root = repo.getRootNode();
//		root.commit( "Testing purposes", new String[0] );
//		JsonObject branchInfo = root.branch( "Testing" );
//		System.out.println( branchInfo.toString() );
	}
	
	
	
}
