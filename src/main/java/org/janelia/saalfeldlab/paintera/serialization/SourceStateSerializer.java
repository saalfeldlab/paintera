package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class SourceStateSerializer implements JsonSerializer< SourceStateWithIndexedDependencies< ?, ? > >
{

	public static final String STATE_KEY = "state";

	public static final String STATE_TYPE_KEY = "stateType";

	public static final String DEPENDS_ON_KEY = "dependsOn";

	@Override
	public JsonElement serialize( final SourceStateWithIndexedDependencies< ?, ? > src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final JsonObject map = new JsonObject();
		final SourceState< ?, ? > state = src.state();
		map.addProperty( STATE_TYPE_KEY, state.getClass().getName() );
		map.add( DEPENDS_ON_KEY, context.serialize( src.dependsOn() ) );
		map.add( STATE_KEY, context.serialize( state, state.getClass() ) );
		return map;
	}

}
