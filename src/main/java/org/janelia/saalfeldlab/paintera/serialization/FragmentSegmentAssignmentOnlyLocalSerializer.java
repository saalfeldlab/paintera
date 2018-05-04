package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class FragmentSegmentAssignmentOnlyLocalSerializer implements JsonSerializer< FragmentSegmentAssignmentOnlyLocal >
{

	public static final String FRAGMENTS_KEY = "fragments";

	public static final String SEGMENTS_KEY = "segments";

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
