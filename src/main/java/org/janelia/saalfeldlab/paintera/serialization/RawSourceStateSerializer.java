package org.janelia.saalfeldlab.paintera.serialization;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.JsonObject;

import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;

public class RawSourceStateSerializer extends SourceStateSerializerInterface< RawSourceState< ?, ? >, ARGBColorConverter< ? > >
{

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	protected RawSourceState< ?, ? > makeState(
			final JsonObject map,
			final DataSource< ?, ? > source,
			final ARGBColorConverter< ? > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final SourceState< ?, ? >[] dependsOn )
	{
		return new RawSourceState( source, converter, composite, name );
	}

}
