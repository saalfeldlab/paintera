package org.janelia.saalfeldlab.paintera.serialization;

import java.util.Optional;

import bdv.viewer.Source;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;

public interface PainteraSource
{
	public Optional<Source<?>> addToViewer(PainteraBaseView viewer);

	public boolean isDirty();

	public boolean clean();
}