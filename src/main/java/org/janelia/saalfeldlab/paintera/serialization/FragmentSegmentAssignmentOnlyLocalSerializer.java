package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class FragmentSegmentAssignmentOnlyLocalSerializer implements
		JsonSerializer< FragmentSegmentAssignmentOnlyLocal >,
		JsonDeserializer< FragmentSegmentAssignmentOnlyLocal >
{

	private static final String FRAGMENTS_KEY = "fragments";

	private static final String SEGMENTS_KEY = "segments";

	@Override
	public FragmentSegmentAssignmentOnlyLocal deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
	{
		final JsonObject obj = json.getAsJsonObject();
		final long[] keys = context.deserialize( obj.get( FRAGMENTS_KEY ), long[].class );
		final long[] values = context.deserialize( obj.get( SEGMENTS_KEY ), long[].class );
		return new FragmentSegmentAssignmentOnlyLocal( keys, values, ( k, v ) -> {} );
	}

	@Override
	public JsonElement serialize( final FragmentSegmentAssignmentOnlyLocal src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final int size = src.size();
		final long[] keys = new long[ size ];
		final long[] values = new long[ size ];
		src.persist( keys, values );
		final JsonObject obj = new JsonObject();
		obj.add( FRAGMENTS_KEY, context.serialize( keys ) );
		obj.add( SEGMENTS_KEY, context.serialize( values ) );
		return obj;
	}

}
