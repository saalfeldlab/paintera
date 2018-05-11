package org.janelia.saalfeldlab.paintera.serialization;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.MinimalSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.JsonObject;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class MinimalSourceStateSerializer extends SourceStateSerializerInterface< MinimalSourceState< ?, ?, Converter< ?, ARGBType > >, Converter< ?, ARGBType > >
{

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	protected MinimalSourceState< ?, ?, Converter< ?, ARGBType > > makeState(
			final JsonObject map,
			final DataSource< ?, ? > source,
			final Converter< ?, ARGBType >  converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final SourceState< ?, ? >[] dependsOn )
	{
		return new MinimalSourceState( source, converter, composite, name, dependsOn );
	}

}
