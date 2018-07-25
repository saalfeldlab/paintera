package org.janelia.saalfeldlab.paintera.control.assignment;

public class UnableToPersist extends Exception
{

	public UnableToPersist()
	{
		this("Unable to persist.");
	}

	public UnableToPersist(final Throwable cause)
	{
		super("Unable to persist.", cause);
	}

	public UnableToPersist(final String message)
	{
		super(message);
	}

}
