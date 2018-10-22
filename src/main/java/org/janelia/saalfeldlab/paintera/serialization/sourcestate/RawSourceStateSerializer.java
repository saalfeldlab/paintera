package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.scijava.plugin.Plugin;

@Plugin(type = PainteraSerialization.PainteraSerializer.class)
public class RawSourceStateSerializer extends
                                      SourceStateSerialization.SourceStateSerializerWithoutDependencies<RawSourceState<?, ?>>
		implements PainteraSerialization.PainteraSerializer<RawSourceState<?, ?>>
{

	@Override
	public Class<RawSourceState<?, ?>> getTargetClass() {
		return (Class<RawSourceState<?, ?>>) (Class<?>) RawSourceState.class;
	}
}
