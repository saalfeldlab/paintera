package bdv.bigcat.viewer.atlas.solver.action;

import java.util.Arrays;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Detach implements Action
{

	public static String IDENTIFIER = "separate";

	private final long id;

	private final long[] from;

	public Detach( final long id, final long... from )
	{
		super();
		this.id = id;
		this.from = from;
	}

	public long id()
	{
		return id;
	}

	public long[] from()
	{
		return from;
	}

	@Override
	public String identifier()
	{
		return IDENTIFIER;
	}

	@Override
	public JsonElement jsonData()
	{
		final JsonObject json = new JsonObject();
		json.addProperty( "fragment", id );
		final JsonArray arr = new JsonArray();
		Arrays.stream( from() ).forEach( arr::add );
		json.add( "from", arr );
		return json;
	}

	public static Detach fromJson( final JsonObject json )
	{
		final long id = json.get( "fragment" ).getAsLong();
		final JsonArray array = json.get( "from" ).getAsJsonArray();
		final long[] from = new long[ array.size() ];
		for ( int i = 0; i < from.length; ++i )
			from[ i ] = array.get( i ).getAsLong();
		return new Detach( id, from );
	}

}
