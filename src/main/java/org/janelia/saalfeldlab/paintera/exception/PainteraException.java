package org.janelia.saalfeldlab.paintera.exception;

public abstract class PainteraException extends Exception {

	protected PainteraException()
	{
		super();
	}

	protected PainteraException(String message)
	{
		super(message);
	}

	protected PainteraException(String message, Throwable cause)
	{
		super(message, cause);
	}

	protected PainteraException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}

	protected PainteraException(Throwable cause)
	{
		super(cause);
	}

}
