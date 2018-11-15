package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;

public interface HasHighlightingStreamConverter<T> {

	HighlightingStreamConverter<T> highlightingStreamConverter();

}
