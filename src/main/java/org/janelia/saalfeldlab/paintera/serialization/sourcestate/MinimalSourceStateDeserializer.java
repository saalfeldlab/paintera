package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.util.function.IntFunction;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.MinimalSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;

public class MinimalSourceStateDeserializer
		extends
		SourceStateSerialization.SourceStateDeserializerWithDependencies<MinimalSourceState<?, ?, ?, Converter<?,
				ARGBType>>, Converter<?, ARGBType>>
{

	public MinimalSourceStateDeserializer(final IntFunction<SourceState<?, ?>> stateFromIndex)
	{
		super(stateFromIndex);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	protected MinimalSourceState<?, ?, ?, Converter<?, ARGBType>> makeState(
			final JsonObject map,
			final DataSource<?, ?> source,
			final Composite<ARGBType, ARGBType> composite,
			final Converter<?, ARGBType> converter,
			final String name,
			final SourceState<?, ?>[] dependsOn,
			final JsonDeserializationContext context) throws ClassNotFoundException
	{
		return new MinimalSourceState(source, converter, composite, name, dependsOn);
	}

}
