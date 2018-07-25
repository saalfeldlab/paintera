package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.lang.invoke.MethodHandles;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawSourceStateDeserializer extends
                                        SourceStateSerialization
		                                        .SourceStateDeserializerWithoutDependencies<RawSourceState<?, ?>,
		                                        ARGBColorConverter<?>>
{

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	protected RawSourceState<?, ?> makeState(
			final JsonObject map,
			final DataSource<?, ?> source,
			final Composite<ARGBType, ARGBType> composite,
			final ARGBColorConverter<?> converter,
			final String name,
			final SourceState<?, ?>[] dependsOn,
			final JsonDeserializationContext context) throws ClassNotFoundException
	{
		LOG.debug("Initializing raw source state with {} {} {} {}", source, converter, composite, name);
		return new RawSourceState(source, converter, composite, name);
	}

}
