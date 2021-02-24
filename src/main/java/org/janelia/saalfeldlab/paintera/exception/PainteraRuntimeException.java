package org.janelia.saalfeldlab.paintera.exception;

public class PainteraRuntimeException extends RuntimeException {

  public PainteraRuntimeException() {

	super();
  }

  public PainteraRuntimeException(String message) {

	super(message);
  }

  public PainteraRuntimeException(String message, Throwable cause) {

	super(message, cause);
  }

  public PainteraRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {

	super(message, cause, enableSuppression, writableStackTrace);
  }

  public PainteraRuntimeException(Throwable cause) {

	super(cause);
  }

}
