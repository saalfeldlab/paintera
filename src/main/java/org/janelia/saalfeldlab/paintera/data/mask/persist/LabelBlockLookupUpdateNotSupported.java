package org.janelia.saalfeldlab.paintera.data.mask.persist;

import org.janelia.saalfeldlab.paintera.exception.PainteraException;

public class LabelBlockLookupUpdateNotSupported extends UnableToUpdateLabelBlockLookup {

	public LabelBlockLookupUpdateNotSupported(String message)
	{
		super(message);
	}

	public LabelBlockLookupUpdateNotSupported(String message, Throwable cause)
	{
		super(message, cause);
	}

}
