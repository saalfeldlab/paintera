package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.SourceState;

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

public abstract class SourceStateSerializerInterface< S extends SourceState< ?, ? >, C extends Converter< ?, ARGBType > > implements JsonSerializer< S >, JsonDeserializer< S >
{

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
			final JsonObject map = el.getAsJsonObject();
			final Class< ? extends Composite< ARGBType, ARGBType > > compositeClass = ( Class< ? extends Composite< ARGBType, ARGBType > > ) Class.forName( map.get( COMPOSITE_TYPE_KEY ).getAsString() );
			final Class< ? extends C > converterClass = ( Class< ? extends C > ) Class.forName( map.get( CONVERTER_TYPE_KEY ).getAsString() );
			final Class< ? extends DataSource< ?, ? > > dataSourceClass = ( Class< ? extends DataSource< ?, ? > > ) Class.forName( map.get( SOURCE_TYPE_KEY ).getAsString() );

			final Composite< ARGBType, ARGBType > composite = context.deserialize( map.get( COMPOSITE_KEY ), compositeClass );
			final C converter = context.deserialize( map.get( CONVERTER_KEY ), converterClass );
			final DataSource< ?, ? > dataSource = context.deserialize( map.get( SOURCE_KEY ), dataSourceClass );
			final String name = map.get( NAME_KEY ).getAsString();
			final boolean isVisible = map.get( IS_VISIBLE_KEY ).getAsBoolean();
			final Interpolation interpolation = context.deserialize( map.get( INTERPOLATION_KEY ), Interpolation.class );

			final SourceState< ?, ? >[] dependsOn = null;
			final S state = makeState( map, dataSource, converter, composite, name, dependsOn );
			state.isVisibleProperty().set( isVisible );
			state.interpolationProperty().set( interpolation );
			return state;
		}
		catch ( final ClassNotFoundException e )
		{
			throw new JsonParseException( e );
		}
	}

	protected abstract S makeState(
			JsonObject map,
			DataSource< ?, ? > source,
			C converter,
			Composite< ARGBType, ARGBType >composite,
			String name,
			SourceState< ?, ? >[] dependsOn );

}
