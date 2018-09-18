package org.janelia.saalfeldlab.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.nio.file.NoSuchFileException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MakeUnchecked
{

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static interface CheckedFunction<T, U>
	{
		public U apply(T t) throws Exception;
	}

	public static interface CheckedSupplier<T>
	{
		public T get() throws Exception;
	}

	public static interface CheckedConsumer<T>
	{
		public void accept(T t) throws Exception;
	}

	public static <T, U> Function<T, U> orElse(final CheckedFunction<T, U> func, final Function<T, U> onExcept)
	{
		return t -> {
			try
			{
				return func.apply(t);
			} catch (final Exception e)
			{
				return onExcept.apply(t);
			}
		};
	}

	public static <T, U> Function<T, U> function(final CheckedFunction<T, U> func, final Function<T, U> fallback)
	{
		return t -> {
			try
			{
				final U val = func.apply(t);
				LOG.debug("Got val {}", val);
				return val;
			}
			catch (final Exception e)
			{
				LOG.debug("Caught exception, using fallback instead.", e);
				return fallback.apply(t);
			}
		};
	}

	public static <T, U> Function<T, U> function(final CheckedFunction<T, U> func)
	{
		return t -> {
			try
			{
				return func.apply(t);
			} catch (final Exception e)
			{
				if (e instanceof RuntimeException) { throw (RuntimeException) e; }
				throw new RuntimeException(e);
			}
		};
	}

	public static <T> Consumer<T> onException(final CheckedConsumer<T> consumer, final BiConsumer<T, Exception>
			onException)
	{
		return t -> {
			try
			{
				consumer.accept(t);
			} catch (final Exception e)
			{
				onException.accept(t, e);
			}
		};
	}

	public static <T> Consumer<T> unchecked(final CheckedConsumer<T> consumer)
	{
		return t -> {
			try
			{
				consumer.accept(t);
			} catch (final Exception e)
			{
				throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
			}
		};
	}

	public static interface CheckedRunnable
	{
		public void run() throws Exception;
	}

	public static <T> Supplier<T> supplier(final CheckedSupplier<T> supplier)
	{
		return supplier(supplier, e -> {throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e);});
	}

	public static <T> Supplier<T> supplier(final CheckedSupplier<T> supplier, Function<Exception, T> handler)
	{
		return () -> {
			try
			{
				return supplier.get();
			} catch (final Exception e)
			{
				return handler.apply(e);
			}
		};
	}

	public static Runnable runnable(final CheckedRunnable runnable)
	{
		return () -> {
			try
			{
				runnable.run();
			} catch (final Exception e)
			{
				if (e instanceof RuntimeException) { throw (RuntimeException) e; }
				throw new RuntimeException(e);
			}
		};
	}

}
