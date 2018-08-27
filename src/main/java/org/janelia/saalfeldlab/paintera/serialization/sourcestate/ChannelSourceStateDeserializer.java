package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class ChannelSourceStateDeserializer extends
		SourceStateSerialization
				.SourceStateDeserializerWithoutDependencies<ChannelSourceState<?, ?, ?, ?>,
				ARGBCompositeColorConverter<?, ?, ?>> {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	protected ChannelSourceState<?, ?, ?, ?> makeState(
			final JsonObject map,
			final DataSource<?, ?> source,
			final Composite<ARGBType, ARGBType> composite,
			final ARGBCompositeColorConverter<?, ?, ?> converter,
			final String name,
			final SourceState<?, ?>[] dependsOn,
			final JsonDeserializationContext context) throws ClassNotFoundException {
		LOG.debug("Initializing raw source state with {} {} {} {}", source, converter, composite, name);
		return new ChannelSourceState<>((ChannelDataSource) source, (ARGBCompositeColorConverter) converter, composite, name);
	}

}
