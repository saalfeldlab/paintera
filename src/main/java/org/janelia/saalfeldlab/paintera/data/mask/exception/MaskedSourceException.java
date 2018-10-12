package org.janelia.saalfeldlab.paintera.data.mask.exception;

import org.janelia.saalfeldlab.paintera.exception.PainteraException;

public abstract class MaskedSourceException extends PainteraException {

	protected MaskedSourceException(String message, Throwable cause)
	{
		super(message, cause);
	}

	protected MaskedSourceException(String message)
	{
		super(message);
	}

}
