package org.janelia.saalfeldlab.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class MakeUnchecked {

  private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Deprecated
  public interface CheckedFunction<T, U> {

	U apply(T t) throws Exception;
  }

  @Deprecated
  public interface CheckedConsumer<T> {

	void accept(T t) throws Exception;
  }

  @Deprecated
  public static <T, U> Function<T, U> function(final CheckedFunction<T, U> func, final Function<T, U> fallback) {

	return t -> {
	  try {
		final U val = func.apply(t);
		LOG.debug("Got val {}", val);
		return val;
	  } catch (final Exception e) {
		LOG.debug("Caught exception, using fallback instead.", e);
		return fallback.apply(t);
	  }
	};
  }

  @Deprecated
  public static <T> Consumer<T> onException(
		  final CheckedConsumer<T> consumer,
		  final BiConsumer<T, Exception> onException) {

	return t -> {
	  try {
		consumer.accept(t);
	  } catch (final Exception e) {
		onException.accept(t, e);
	  }
	};
  }

}
