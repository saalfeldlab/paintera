package bdv.bigcat.viewer.atlas.solver.action;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ConfirmGroupings implements Action
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

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
		LOG.debug( "MAKING JSON_DATA {}", Arrays.toString( fragmentsBySegment ) );
		Arrays.stream( fragmentsBySegment ).forEach( array -> {
			LOG.trace( "DETAILED: MAKING JSON_DATA {}", Arrays.toString( array ) );
			final JsonArray localIds = new JsonArray();
			Arrays.stream( array ).forEach( localIds::add );
			ids.add( localIds );
		} );
		json.add( "fragments", ids );
		return json;
	}

	public static ConfirmGroupings fromJson( final JsonObject json )
	{
		LOG.debug( "GENERATING FROM JSON {}", json );
		final JsonArray fragmentsJson = json.get( "fragments" ).getAsJsonArray();
		final long[][] fragments = new long[ fragmentsJson.size() ][];
		for ( int k = 0; k < fragments.length; ++k )
		{
			final JsonArray localFragmentsJson = fragmentsJson.get( k ).getAsJsonArray();
			final long[] frag = new long[ localFragmentsJson.size() ];
			for ( int i = 0; i < frag.length; ++i )
				frag[ i ] = localFragmentsJson.get( i ).getAsLong();
			fragments[ k ] = frag;
		}
		return new ConfirmGroupings( fragments );
	}

}
