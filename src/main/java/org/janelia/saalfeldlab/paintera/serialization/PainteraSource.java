package org.janelia.saalfeldlab.paintera.serialization;

import bdv.viewer.Source;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;

import java.util.Optional;

public interface PainteraSource {

	public Optional<Source<?>> addToViewer(PainteraBaseView viewer);

	public boolean isDirty();

	public boolean clean();
}
