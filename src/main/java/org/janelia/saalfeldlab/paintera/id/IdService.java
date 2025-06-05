package org.janelia.saalfeldlab.paintera.id;

import java.util.Iterator;
import java.util.Random;
import java.util.stream.LongStream;

public interface IdService {

	// Labels in this should not be persisted, other than the reserved values above
	long FIRST_TEMPORARY_ID = 0xfff0ffffffffffffL;
	long LAST_TEMPORARY_ID = 0xfff1ffffffffffffL;
	Iterator<Long> randomTemps = new Random().longs(FIRST_TEMPORARY_ID, LAST_TEMPORARY_ID).iterator();


	/**
	 * Invalidate an ID. Sets the next ID of this service if the passed ID is greater than the current next ID.
	 *
	 * @param id
	 */
	public void invalidate(final long id);

	/**
	 * return the result of the previous call to `next()`.
	 * next() == current() should always be true.
	 * It is not valid to call `current()` before the first call the `next()`
	 *
	 * @return the last value of `next()`
	 */
	public long current();

	/**
	 * Get the next ID.
	 *
	 * @return
	 */
	public long next();

	/**
	 * Get the n next IDs.
	 *
	 * @param n
	 * @return
	 */
	public long[] next(final int n);

	/**
	 * Get the next temporary ID. These should only be used for non-persistent labels.
	 * There is no gaurantee that the same temporary ID will not be return, as they will be reused.
	 * The implementation should allow for a large number of possible temporary Ids.
	 *
	 * @return a temporary ID
	 */
	long nextTemporary();

	/**
	 * Get the next n temporary IDs.
	 *
	 * @param n the number of temporary Ids to receive
	 * @return an array of temporary ids
	 */
	long[] nextTemporary(final int n);

	/**
	 * Check if {@code id} was invalidated, e.g. when provided through {@link #next()}.
	 *
	 * @param id
	 * @return
	 */
	public boolean isInvalidated(final long id);

	/**
	 * Greater than comparison for two uint64 passed as long.
	 *
	 * @param a
	 * @param b
	 * @return
	 */
	static public boolean greaterThan(final long a, final long b) {

		return a + Long.MIN_VALUE > b + Long.MIN_VALUE;
	}

	/**
	 * Max of two uint64 passed as long.
	 *
	 * @param a
	 * @param b
	 * @return
	 */
	static public long max(final long a, final long b) {

		return a + Long.MIN_VALUE > b + Long.MIN_VALUE ? a : b;
	}

	/**
	 * Max of a stream of uint64 passed as long.
	 *
	 * @param ids
	 * @return
	 */
	static public long max(final LongStream ids) {

		return ids.reduce(0, (a, b) -> max(a, b));
	}

	static public long max(final long[] ids) {

		return max(LongStream.of(ids));
	}

	static long randomTemporaryId() {
		return randomTemps.next();
	}

	static boolean isTemporary(final long id) {

		return id >= FIRST_TEMPORARY_ID && id < LAST_TEMPORARY_ID;
	}

	class IdServiceNotProvided implements IdService {

		@Override
		public void invalidate(long id) {

			throw new UnsupportedOperationException(String.format("%s does not support any operation at all!", this.getClass().getName()));
		}

		@Override
		public long current() {

			throw new UnsupportedOperationException(String.format("%s does not support any operation at all!", this.getClass().getName()));
		}

		@Override
		public long next() {

			throw new UnsupportedOperationException(String.format("%s does not support any operation at all!", this.getClass().getName()));
		}

		@Override
		public long[] next(int n) {

			throw new UnsupportedOperationException(String.format("%s does not support any operation at all!", this.getClass().getName()));
		}

		@Override public long nextTemporary() {

			throw new UnsupportedOperationException(String.format("%s does not support any operation at all!", this.getClass().getName()));
		}

		@Override public long[] nextTemporary(int n) {

			throw new UnsupportedOperationException(String.format("%s does not support any operation at all!", this.getClass().getName()));
		}

		@Override
		public boolean isInvalidated(long id) {

			throw new UnsupportedOperationException(String.format("%s does not support any operation at all!", this.getClass().getName()));
		}
	}
}
