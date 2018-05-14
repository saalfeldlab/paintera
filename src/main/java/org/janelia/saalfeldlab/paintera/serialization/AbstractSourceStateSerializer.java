package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import bdv.viewer.Interpolation;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public abstract class AbstractSourceStateSerializer< S extends SourceState< ?, ? >, C extends Converter< ?, ARGBType > > implements JsonSerializer< S >, JsonDeserializer< S >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final String COMPOSITE_TYPE_KEY = "compositeType";

	public static final String CONVERTER_TYPE_KEY = "converterType";

	public static final String COMPOSITE_KEY = "composite";

	public static final String CONVERTER_KEY = "converter";

	public static final String INTERPOLATION_KEY = "interpolation";

	public static final String IS_VISIBLE_KEY = "isVisible";

	public static final String SOURCE_TYPE_KEY = "sourceType";

	public static final String SOURCE_KEY = "source";

	public static final String NAME_KEY = "name";

	@Override
	public JsonObject serialize( final S state, final Type type, final JsonSerializationContext context )
	{
		final JsonObject map = new JsonObject();
		map.add( COMPOSITE_KEY, context.serialize( state.compositeProperty().get() ) );
		map.add( CONVERTER_KEY, context.serialize( state.converter() ) );
		map.addProperty( COMPOSITE_TYPE_KEY, state.compositeProperty().get().getClass().getName() );
		map.addProperty( CONVERTER_TYPE_KEY, state.converter().getClass().getName() );
		map.add( INTERPOLATION_KEY, context.serialize( state.interpolationProperty().get() ) );
		map.addProperty( IS_VISIBLE_KEY, state.isVisibleProperty().get() );
		map.addProperty( SOURCE_TYPE_KEY, state.getDataSource().getClass().getName() );
		map.add( SOURCE_KEY, context.serialize( state.getDataSource() ) );
		map.addProperty( NAME_KEY, state.nameProperty().get() );
		return map;
	}

	@SuppressWarnings( "unchecked" )
	@Override
	public S deserialize( final JsonElement el, final Type type, final JsonDeserializationContext context ) throws JsonParseException
	{
		try
		{
			final JsonObject map = el.getAsJsonObject().get( SourceStateSerializer.STATE_KEY ).getAsJsonObject();
			Log.debug( "composite class: {} (key={})", map.get( COMPOSITE_TYPE_KEY ), COMPOSITE_TYPE_KEY );
			final Class< ? extends Composite< ARGBType, ARGBType > > compositeClass = ( Class< ? extends Composite< ARGBType, ARGBType > > ) Class.forName( map.get( COMPOSITE_TYPE_KEY ).getAsString() );
			final Class< ? extends DataSource< ?, ? > > dataSourceClass = ( Class< ? extends DataSource< ?, ? > > ) Class.forName( map.get( SOURCE_TYPE_KEY ).getAsString() );

			final Composite< ARGBType, ARGBType > composite = context.deserialize( map.get( COMPOSITE_KEY ), compositeClass );
			final DataSource< ?, ? > dataSource = context.deserialize( map.get( SOURCE_KEY ), dataSourceClass );
			final String name = map.get( NAME_KEY ).getAsString();
			final boolean isVisible = map.get( IS_VISIBLE_KEY ).getAsBoolean();
			LOG.debug( "Is visible? {}", isVisible );
			final Interpolation interpolation = context.deserialize( map.get( INTERPOLATION_KEY ), Interpolation.class );

			final SourceState< ?, ? >[] dependsOn = null;
			final S state = makeState( map, dataSource, composite, name, dependsOn, context );
			LOG.warn( "Got state {}", state );
			state.isVisibleProperty().set( isVisible );
			state.interpolationProperty().set( interpolation );
			return state;
		}
		catch ( final Exception e )
		{
			LOG.debug( "Caught exception {}", e.getMessage() );
			e.printStackTrace();
			System.out.print( 123 );
			throw e instanceof JsonParseException ? (JsonParseException) e : new JsonParseException( e );
		}
	}

	protected static <C extends Converter< ?, ARGBType >> C converterFromJsonObject( final JsonObject data, final JsonDeserializationContext context ) throws ClassNotFoundException
	{

		@SuppressWarnings( "unchecked" )
		final Class< ? extends C > converterClass = ( Class< ? extends C > ) Class.forName( data.get( CONVERTER_TYPE_KEY ).getAsString() );
		final C converter = context.deserialize( data.get( CONVERTER_KEY ), converterClass );
		return converter;
	}

	protected abstract S makeState(
			JsonObject map,
			DataSource< ?, ? > source,
			Composite< ARGBType, ARGBType >composite,
			String name,
			SourceState< ?, ? >[] dependsOn,
			JsonDeserializationContext context ) throws Exception;

}
