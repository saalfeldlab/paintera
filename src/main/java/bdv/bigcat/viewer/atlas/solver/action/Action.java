package bdv.bigcat.viewer.atlas.solver.action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public interface Action
{
	// marker interface for actions
	public String identifier();

	public JsonElement jsonData();

	default public JsonObject toJson()
	{
		final JsonObject json = new JsonObject();
		json.addProperty( "type", identifier() );
		json.add( "data", jsonData() );
		return json;
	}

	public static JsonArray toJson( final Collection< Action > actions )
	{
		final JsonArray json = new JsonArray();
		actions.stream().map( Action::toJson ).forEach( json::add );
		return json;
	}

	public static List< Action > fromJson( final String json )
	{
		final ArrayList< Action > actions = new ArrayList<>();
		final JsonElement jsonEl = new JsonParser().parse( json );
		if ( !( jsonEl instanceof JsonArray ) )
			return actions;

		final JsonArray array = ( JsonArray ) jsonEl;
		for ( final JsonElement el : array )
		{
			if ( !( el instanceof JsonObject ) || !( ( JsonObject ) el ).has( "type" ) )
				continue;
			final JsonObject obj = ( JsonObject ) el;
			final String type = obj.get( "type" ).getAsString();
			if ( type.equals( Detach.IDENTIFIER ) )
				actions.add( Detach.fromJson( obj.get( "data" ).getAsJsonObject() ) );
			else if ( type.equals( Merge.IDENTIFIER ) )
				actions.add( Merge.fromJson( obj.get( "data" ).getAsJsonObject() ) );
			else if ( type.equals( ConfirmSingleSegment.IDENTIFIER ) )
				actions.add( ConfirmSingleSegment.fromJson( obj.get( "data" ).getAsJsonObject() ) );
			else if ( type.equals( ConfirmGroupings.IDENTIFIER ) )
			{
				final JsonObject data = obj.get( "data" ).getAsJsonObject();
				actions.add( ConfirmGroupings.fromJson( data ) );
			}
		}

		return actions;
	}

}
