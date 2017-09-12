package bdv.bigcat.viewer.atlas.solver.action;

import java.util.Arrays;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ConfirmGroupings implements Action
{

	public static String IDENTIFIER = "confirm-multiple-segments";

	private final long[][] fragmentsBySegment;

	public ConfirmGroupings( final long[]... fragmentsBySegment )
	{
		super();
		this.fragmentsBySegment = fragmentsBySegment;
	}

	public final long[][] fragmentsBySegment()
	{
		return fragmentsBySegment;
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
		final JsonArray ids = new JsonArray();
		Arrays.stream( fragmentsBySegment ).forEach( array -> {
			final JsonArray localIds = new JsonArray();
			Arrays.stream( array ).forEach( localIds::add );
			ids.add( localIds );
		} );
		json.add( "fragments", ids );
		return json;
	}

	public static ConfirmGroupings fromJson( final JsonObject json )
	{
		final JsonArray fragmentsJson = json.get( "fragments" ).getAsJsonArray();
		final long[][] fragments = new long[ fragmentsJson.size() ][];
		for ( int k = 0; k < fragments.length; ++k )
		{
			final JsonArray localFragmentsJson = fragmentsJson.get( k ).getAsJsonArray();
			final long[] frag = new long[ localFragmentsJson.size() ];
			for ( int i = 0; i < frag.length; ++i )
				frag[ i ] = localFragmentsJson.get( i ).getAsLong();
		} ;
		return new ConfirmGroupings( fragments );
	}

}
