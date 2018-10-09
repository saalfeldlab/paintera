package org.janelia.saalfeldlab.paintera.exception;

public abstract class PainteraRuntimeException extends RuntimeException {

	protected PainteraRuntimeException()
	{
		super();
	}

	protected PainteraRuntimeException(String message)
	{
		super(message);
	}

	protected PainteraRuntimeException(String message, Throwable cause)
	{
		super(message, cause);
	}

	protected PainteraRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}

	protected PainteraRuntimeException(Throwable cause)
	{
		super(cause);
	}

}
