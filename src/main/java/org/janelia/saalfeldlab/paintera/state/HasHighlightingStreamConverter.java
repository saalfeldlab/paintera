package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;

@Deprecated
public interface HasHighlightingStreamConverter<T> {

	HighlightingStreamConverter<T> highlightingStreamConverter();

}
