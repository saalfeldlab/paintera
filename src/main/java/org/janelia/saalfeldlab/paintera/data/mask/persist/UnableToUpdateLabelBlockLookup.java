package org.janelia.saalfeldlab.paintera.data.mask.persist;

import org.janelia.saalfeldlab.paintera.exception.PainteraException;

public class UnableToUpdateLabelBlockLookup extends PainteraException {

	public UnableToUpdateLabelBlockLookup(String message)
	{
		super(message);
	}

	public UnableToUpdateLabelBlockLookup(String message, Throwable cause)
	{
		super(message, cause);
	}

}
