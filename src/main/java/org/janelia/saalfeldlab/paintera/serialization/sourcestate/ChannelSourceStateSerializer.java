package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.scijava.plugin.Plugin;

@Plugin(type = PainteraSerialization.PainteraSerializer.class)
public class ChannelSourceStateSerializer extends
		SourceStateSerialization.SourceStateSerializerWithoutDependencies<ChannelSourceState<?, ?, ?, ?>>
	implements PainteraSerialization.PainteraSerializer<ChannelSourceState<?, ?, ?, ?>>
{

	@Override
	public Class<ChannelSourceState<?, ?, ?, ?>> getTargetClass() {
		return (Class<ChannelSourceState<?, ?, ?, ?>>) (Class<?>) ChannelSourceState.class;
	}
}
