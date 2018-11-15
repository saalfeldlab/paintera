package org.janelia.saalfeldlab.paintera.id;

import java.util.stream.LongStream;

public interface IdService
{
	/**
	 * Invalidate an ID. Sets the next ID of this service if the passed ID is greater than the current next ID.
	 *
	 * @param id
	 */
	public void invalidate(final long id);

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
	 *
	 * @return
	 */
	public long[] next(final int n);

	/**
	 * Check if {@code id} was invalidated, e.g. when provided through {@link #next()}.
	 *
	 * @param id
	 *
	 * @return
	 */
	public boolean isInvalidated(final long id);

	/**
	 * Greater than comparison for two uint64 passed as long.
	 *
	 * @param a
	 * @param b
	 *
	 * @return
	 */
	static public boolean greaterThan(final long a, final long b)
	{
		return a + Long.MIN_VALUE > b + Long.MIN_VALUE;
	}

	/**
	 * Max of two uint64 passed as long.
	 *
	 * @param a
	 * @param b
	 *
	 * @return
	 */
	static public long max(final long a, final long b)
	{
		return a + Long.MIN_VALUE > b + Long.MIN_VALUE ? a : b;
	}

	/**
	 * Max of a stream of uint64 passed as long.
	 *
	 * @param ids
	 *
	 * @return
	 */
	static public long max(final LongStream ids)
	{
		return ids.reduce(0, (a, b) -> max(a, b));
	}

	static public long max(final long[] ids)
	{
		return max(LongStream.of(ids));
	}

	public static IdService dummy()
	{
		return new Dummy();
	}

	class IdServiceNotProvided implements IdService {

		@Override
		public void invalidate(long id) {
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

		@Override
		public boolean isInvalidated(long id) {
			throw new UnsupportedOperationException(String.format("%s does not support any operation at all!", this.getClass().getName()));
		}
	}

	public static class Dummy implements IdService
	{

		private Dummy()
		{

		}

		@Override
		public void invalidate(final long id)
		{
			// TODO Auto-generated method stub

		}

		@Override
		public long next()
		{
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public long[] next(final int n)
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean isInvalidated(final long id)
		{
			// TODO Auto-generated method stub
			return false;
		}

	}
}
