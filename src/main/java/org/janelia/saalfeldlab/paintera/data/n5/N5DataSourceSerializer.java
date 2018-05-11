package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import bdv.util.volatiles.SharedQueue;
import net.imglib2.realtransform.AffineTransform3D;

public class N5DataSourceSerializer implements JsonSerializer< N5DataSource< ?, ? > >, JsonDeserializer< N5DataSource< ?, ? > >
{

	private static final String META_CLASS_KEY = "metaType";

	private static final String META_KEY = "meta";

	private static final String TRANSFORM_KEY = "transform";

	private final SharedQueue sharedQueue;

	private final int priority;

	public N5DataSourceSerializer( SharedQueue sharedQueue, int priority )
	{
		super();
		this.sharedQueue = sharedQueue;
		this.priority = priority;
	}

	@Override
	public JsonElement serialize( N5DataSource< ?, ? > s, Type type, JsonSerializationContext context )
	{
		JsonObject map = new JsonObject();
		map.addProperty( META_CLASS_KEY, s.meta().getClass().getName() );
		map.add( META_KEY, context.serialize( s.meta() ) );
		AffineTransform3D transform = new AffineTransform3D();
		map.add( TRANSFORM_KEY, context.serialize( transform ) );
		return map;
	}

	@Override
	public N5DataSource< ?, ? > deserialize( JsonElement el, Type type, JsonDeserializationContext context ) throws JsonParseException
	{
		try
		{
		String clazz = el.getAsJsonObject().get( META_CLASS_KEY ).getAsString();
		N5Meta meta = (N5Meta)context.deserialize( el.getAsJsonObject().get( META_KEY ), Class.forName( clazz ) );
		AffineTransform3D transform = context.deserialize( el.getAsJsonObject().get( TRANSFORM_KEY ), AffineTransform3D.class );
			return new N5DataSource<>( meta, transform, sharedQueue, "", priority );
		}
		catch ( IOException | ClassNotFoundException e )
		{
			throw new JsonParseException( e );
		}

	}

	public static class Factory implements StatefulSerializer.SerializerAndDeserializer< N5DataSource< ?, ? >, N5DataSourceSerializer >
	{

		@Override
		public N5DataSourceSerializer create( PainteraBaseView state, Supplier< String > projectDirectory )
		{
			return new N5DataSourceSerializer( state.getQueue(), 0 );
		}

	}

}
