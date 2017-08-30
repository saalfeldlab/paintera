package bdv.bigcat.viewer.atlas.solver.action;

import java.util.Arrays;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Merge implements Action
{

	public static final String IDENTIFIER = "merge";

	private final long[] ids;

	public Merge( final long... ids )
	{
		super();
		this.ids = ids;
	}

	public long[] ids()
	{
		return ids;
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
		final JsonArray arr = new JsonArray();
		Arrays.stream( ids() ).forEach( arr::add );
		json.add( "fragments", arr );
		return json;
	}

	public static Merge fromJson( final JsonObject json )
	{
		final JsonArray array = json.get( "fragments" ).getAsJsonArray();
		final long[] ids = new long[ array.size() ];
		for ( int i = 0; i < ids.length; ++i )
			ids[ i ] = array.get( i ).getAsLong();
		return new Merge( ids );
	}

}
