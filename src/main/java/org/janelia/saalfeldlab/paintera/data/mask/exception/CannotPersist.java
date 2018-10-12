package org.janelia.saalfeldlab.paintera.data.mask.exception;

public class CannotPersist extends MaskedSourceException
{

	/**
	 *
	 */
	private static final long serialVersionUID = -9077671183779802768L;

	public CannotPersist(final String message, final Throwable cause)
	{
		super(message, cause);
	}

	public CannotPersist(final String message)
	{
		super(message);
	}

}
