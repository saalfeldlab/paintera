package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;

public class RawSourceStateSerializer extends AbstractSourceStateSerializer< RawSourceState< ?, ? >, ARGBColorConverter< ? > >
{

	private static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	protected RawSourceState< ?, ? > makeState(
			final JsonObject map,
			final DataSource< ?, ? > source,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final SourceState< ?, ? >[] dependsOn,
			final JsonDeserializationContext context ) throws ClassNotFoundException
	{
		final Class< ? > clazz = Class.forName( map.get( AbstractSourceStateSerializer.CONVERTER_TYPE_KEY ).getAsString() );
		if ( !ARGBColorConverter.class.isAssignableFrom( clazz ) ) {
			throw new JsonParseException( clazz.getName() + " is not a sub-class of " + ARGBColorConverter.class.getName() );
		}
		final ARGBColorConverter< ? > converter = context.deserialize( map.get( AbstractSourceStateSerializer.CONVERTER_KEY ), clazz );
		LOG.warn( "Deserialized converter {}", converter );
		return new RawSourceState( source, converter, composite, name );
	}

}
