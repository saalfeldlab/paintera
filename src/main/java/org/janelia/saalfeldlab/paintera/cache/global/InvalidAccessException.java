package org.janelia.saalfeldlab.paintera.cache.global;

public class InvalidAccessException extends Exception {

	private final Object access;

	private final Object expected;

	public InvalidAccessException(
			final Object access,
			final Object expected)
	{
		this (
				String.format("Expected %s but found %s", expected.getClass(), access.getClass()),
				access,
				expected);
	}

	public InvalidAccessException(
			final String message,
			final Object access,
			final Object expected) {
		super(message);
		this.access = access;
		this.expected = expected;
	}
}
