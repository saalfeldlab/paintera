package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.state.MinimalSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class MinimalSourceStateSerializer extends
		SourceStateSerialization.SourceStateSerializerWithDependencies< MinimalSourceState< ?, ?, ?, Converter< ?, ARGBType > > >
{

	public MinimalSourceStateSerializer( final ToIntFunction< SourceState< ?, ? > > stateToIndex )
	{
		super( stateToIndex );
	}

	public static class Factory implements
			StatefulSerializer.Serializer< MinimalSourceState< ?, ?, ?, Converter< ?, ARGBType > >, MinimalSourceStateSerializer >
	{

		@Override
		public MinimalSourceStateSerializer createSerializer(
				final Supplier< String > projectDirectory,
				final ToIntFunction< SourceState< ?, ? > > stateToIndex )
		{
			return new MinimalSourceStateSerializer( stateToIndex );
		}

	}

}
