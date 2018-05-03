package org.janelia.saalfeldlab.paintera.serialization;

import java.util.Optional;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;

import bdv.viewer.Source;

public interface PainteraSource
{
	public Optional< Source< ? > > addToViewer( PainteraBaseView viewer );

	public boolean isDirty();

	public boolean clean();
}