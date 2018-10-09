package org.janelia.saalfeldlab.paintera.data.mask.persist;

import org.janelia.saalfeldlab.paintera.exception.PainteraException;

public class UnableToPersistCanvas extends PainteraException {

	public UnableToPersistCanvas(String message)
	{
		super(message);
	}

	public UnableToPersistCanvas(String message, Throwable cause)
	{
		super(message, cause);
	}

}
