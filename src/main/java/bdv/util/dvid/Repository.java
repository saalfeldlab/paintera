package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map.Entry;

import bdv.util.JsonHelper;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * 
 * Class representing dvid repository.
 * 
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 */
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

	/**
	 * @return {@link Server} on which this resides.
	 */
	public Server getServer()
	{
		return this.server;
	}
	
	/**
	 * @return Uuid of this.
	 */
	public String getUuid()
	{
		return uuid;
	}
	
	/**
	 * @return Base url of this.
	 */
	public String getUrl()
	{
		return server.getApiUrl() + "/repo/" + this.uuid;
	}
	
	/**
	 * @return Base url of root node of this.
	 */
	public String getNodeUrl()
	{
		return server.getApiUrl() +"/node/" + this.uuid;
	}
	
	/**
	 * @return Root node of this repository.
	 */
	public Node getRootNode()
	{
		return new Node( this.uuid, this );
	}
	
	/**
	 * 
	 * Check out commit.
	 * 
	 * @param uuid Identifier for commit.
	 * @return {@link Node} representing commit specified by uuid, if uuid is
	 * present; null otherwise.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public Node checkout( String uuid ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonObject dag = getDAG().get( "Nodes" ).getAsJsonObject();
		for ( Entry< String, JsonElement > node : dag.entrySet() )
			if ( Node.uuidEquivalenceCheck( node.getKey(), uuid ) )
				return new Node( uuid, this );
		return null;
	}
	
	/**
	 * Retrieve information using GET request:
	 * 
	 * GET /api/repo/<uuid>/info
	 * 
	 * @return Information on this repository
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public JsonObject getInfo() throws JsonSyntaxException, JsonIOException, IOException
	{
		String url = DvidUrlOptions.getRequestString( this.server.getApiUrl() + "/repo/" + this.uuid + "/info" );
		JsonObject infoObject = JsonHelper.fetch( url, JsonObject.class );
		return infoObject;
	}
	
	/**
	 * Retrieve DAG of commits.
	 * 
	 * @return DAG of commits.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public JsonObject getDAG() throws JsonSyntaxException, JsonIOException, IOException
	{
		return getInfo().get( "DAG" ).getAsJsonObject();
	}
	
	/**
	 * 
	 * Create data set at root node (initial commit).
	 * 
	 * @param name Name of data set.
	 * @param type Type of data set.
	 * @param sync List of data set names to be synched with.
	 * @return Newly created {@link Dataset}.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public Dataset createDataset( String name, String type, String... sync ) throws MalformedURLException, IOException
	{
		return this.getRootNode().createDataset( name, type, sync );
	}
	
	/**
	 * Remove a data set from this repository.
	 * 
	 * @param name Name of data set to be deleted.
	 * @return HTTP status code of DELETE request.
	 * @throws IOException
	 */
	public int deleteDataset( String name ) throws IOException
	{
		return this.getRootNode().deleteDataset( name );
	}
	
	/**
	 * Remove a data set from this repository.
	 * 
	 * @param dataset Data set to be deleted.
	 * @return HTTP status code of DELETE request.
	 * @throws IOException
	 */
	public int deleteDatset( Dataset dataset ) throws IOException
	{
		return deleteDataset( dataset.getName() );
	}
	
	/**
	 * 
	 * Remove this from server.
	 * 
	 * @return HTTP status code of DELETE request.
	 * @throws IOException
	 */
	public int deleteSelf() throws IOException
	{
		return getServer().deleteRepo( this );
	}
	
	/**
	 * 
	 * Helper function to create JsonObject holding alias and description.
	 * 
	 * @param alias
	 * @param description
	 * @return {@link JsonObject}
	 * 
	 */
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
		
//		System.out.println( repo.getServer().getApiUrl() );
//		System.out.println( repo.getUuid() );
		
//		Dataset ds = repo.create( "testing123456", "labelblk" );
//		Dataset ds = new Dataset( repo.getRootNode(), "testing123456" );
		
//		System.out.println( ds.getName() );
//		System.out.println( ds.getNode().getUuid() );
//		System.out.println( ds.getInfo().toString() );
//		
		System.out.println();
//		Repository r2 = new Repository( repo.getServer(), "6efb517b5ca64b67b8d53be310a9bca4" );
//		Node n2 = r2.getRootNode();
//		Dataset d2 = new Dataset( n2, "some-data-set" );
//		System.out.println( repo.getInfo() );
//		Dataset d3 = n3.createDataset( "delete-test", DatasetBlkLabel.TYPE );
		Repository r3 = repo.getServer().createRepo( "delete-test" );
		System.out.println( r3.getInfo() );
//		Node n3 = r3.getRootNode();
		DatasetBlkLabel d3 = ( DatasetBlkLabel ) r3.createDataset( "delete-test", DatasetBlkLabel.TYPE );// new DatasetBlkLabel( n3, "delete-test" );
		System.out.println( d3.getInfo() );
		int status = d3.deleteSelf();
		System.out.println( status );
		System.out.println( d3.getInfo() );
		r3.deleteSelf();
		System.out.println( r3.getInfo() );
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
