package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.function.Function;

public interface InterruptibleFunction<T, R> extends Function<T, R>, Interruptible<T>
{

	public static <T, R> InterruptibleFunction<T, R> fromFunctionAndInterruptible(
			final Function<T, R> function,
			final Interruptible<T> interruptible)
	{
		return new InterruptibleFunction<T, R>()
		{

			@Override
			public R apply(final T t)
			{
				return function.apply(t);
			}

			@Override
			public void interruptFor(final T t)
			{
				interruptible.interruptFor(t);
			}
		};
	}

	public static <T, R> InterruptibleFunction<T, R> fromFunction(final Function<T, R> function)
	{
		return fromFunctionAndInterruptible(function, t -> {
		});
	}

	@SuppressWarnings("unchecked")
	public static <T, R> InterruptibleFunction<T, R>[] fromFunction(final Function<T, R>[] functions)
	{
		return Arrays
				.stream(functions)
				.map(InterruptibleFunction::fromFunction)
				.toArray(InterruptibleFunction[]::new);
	}

}
