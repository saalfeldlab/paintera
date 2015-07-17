package bdv.labels.labelset;



/**
 * A storage container to be accessed by {@link MappedAccess}. The storage can
 * grow, see {@link #resize(long)}, which involves reallocating and copying the
 * underlying primitive array.
 *
 * @param <A>
 *            recursive type of this container type.
 * @param <T>
 *            the {@link MappedAccess} used to access the container.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public interface MappedAccessData< T extends MappedAccess< T > >
{
	/**
	 * Create a new proxy referring to the region at base offset 0.
	 *
	 * @return new access (proxy).
	 */
	public T createAccess();

	/**
	 * Update the given {@link MappedAccess} to refer the region starting at
	 * {@code baseOffset} in this array.
	 */
	public void updateAccess( final T access, final long baseOffset );

	/**
	 * Get the size in bytes of this container.
	 *
	 * @return size of this array in bytes.
	 */
	public long size();

	/**
	 * Set the size in bytes of this container.
	 */
	public void resize( final long size );

	/**
	 * A factory for {@link MappedAccessData}.
	 *
	 * @param <A>
	 *            the type of {@link MappedAccessData} created by this
	 *            factory.
	 */
	public static interface Factory< A extends MappedAccessData< T >, T extends MappedAccess< T > >
	{
		/**
		 * Create container for {@code size} bytes.
		 */
		public A createStorage( final long size );

		/**
		 * Create access (referring to nothing initially).
		 */
		public T createAccess();
	}
}
