/**
 *
 */
package org.janelia.saalfeldlab.util.n5.universe;

import java.util.concurrent.ExecutorService;

/**
 * @author Stephan Saalfeld
 *
 */
public class ASync {

  @FunctionalInterface
  public static interface ThrowingSupplier<T, E extends Exception> {

	public T get() throws E;
  }

  @FunctionalInterface
  public static interface ThrowingConsumer<T, E extends Exception> {

	public void accept(final T t) throws E;
  }

  @FunctionalInterface
  public static interface ThrowingBiConsumer<T, S, E extends Exception> {

	public void accept(final T t, final S s) throws E;
  }

  @FunctionalInterface
  public static interface ThrowingTriConsumer<T, S, Q, E extends Exception> {

	public void accept(final T t, final S s, final Q q) throws E;
  }

  public static <T, E extends Exception, F extends Exception> void async(
		  final ThrowingSupplier<T, E> request,
		  final ThrowingConsumer<T, F> callback) {

	new Thread(() -> {
	  try {
		callback.accept(request.get());
	  } catch (final Exception e) {
		throw new RuntimeException(e);
	  }
	}).run();
  }

  public static <T, E extends Exception, F extends Exception> void async(
		  final ThrowingSupplier<T, E> request,
		  final ThrowingConsumer<T, F> callback,
		  final ThrowingTriConsumer<ThrowingSupplier<T, E>, ThrowingConsumer<T, F>, Exception, Exception> except) {

	new Thread(() -> {
	  try {
		callback.accept(request.get());
	  } catch (final Exception e) {
		try {
		  except.accept(request, callback, e);
		} catch (final Exception f) {
		  throw new RuntimeException(e);
		}
	  }
	}).run();
  }

  public static <T, E extends Exception, F extends Exception> void async(
		  final ThrowingSupplier<T, E> request,
		  final ThrowingConsumer<T, F> callback,
		  final ExecutorService exec) {

	exec.submit(() -> {
	  try {
		callback.accept(request.get());
	  } catch (final Exception e) {
		throw new RuntimeException(e);
	  }
	});
  }

  public static <T, E extends Exception, F extends Exception> void async(
		  final ThrowingSupplier<T, E> request,
		  final ThrowingConsumer<T, F> callback,
		  final ThrowingTriConsumer<ThrowingSupplier<T, E>, ThrowingConsumer<T, F>, Exception, Exception> except,
		  final ExecutorService exec) {

	exec.submit(() -> {
	  try {
		callback.accept(request.get());
	  } catch (final Exception e) {
		try {
		  except.accept(request, callback, e);
		} catch (final Exception f) {
		  throw new RuntimeException(e);
		}
	  }
	});
  }
}