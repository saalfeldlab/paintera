package org.janelia.saalfeldlab.paintera.serialization;

import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

public class StatefulSerializer
{

	public static interface Serializer< T >
	{
		public JsonSerializer< T > create( PainteraBaseView state, Supplier< String > projectDirectory );
	}

	public static interface Deserializer< T >
	{
		public JsonSerializer< T > create( PainteraBaseView state, Supplier< String > projectDirectory );
	}

	public static interface SerializerAndDeserializer< T, S extends JsonDeserializer< T > & JsonSerializer< T > >
	{
		public S create( PainteraBaseView state, Supplier< String > projectDirectory );
	}

}
