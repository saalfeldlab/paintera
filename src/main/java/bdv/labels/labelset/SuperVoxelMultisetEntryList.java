package bdv.labels.labelset;


public class SuperVoxelMultisetEntryList
	extends MappedObjectArrayList< SuperVoxelMultisetEntry, LongMappedAccess >
{
	/**
	 * creates underlying data array
	 */
	public SuperVoxelMultisetEntryList( final int capacity )
	{
		super( SuperVoxelMultisetEntry.type, capacity );
	}

	/**
	 * doesn't create underlying data array
	 */
	protected SuperVoxelMultisetEntryList()
	{
		super( SuperVoxelMultisetEntry.type );
	}

	protected SuperVoxelMultisetEntryList( final LongMappedAccessData data, final long baseOffset )
	{
		super( SuperVoxelMultisetEntry.type, data, baseOffset );
	}

	/**
	 * Performs a binary search for entry with
	 * {@link SuperVoxelMultisetEntry#getId()} <tt>id</tt> in the entire list.
	 * Note that you <b>must</b> @{link #sort sort} the list before doing a
	 * search.
	 *
	 * @param id
	 *            the value to search for
	 * @return the absolute offset in the list of the value, or its negative
	 *         insertion point into the sorted list.
	 */
    public int binarySearch( final long id ) {
        return binarySearch( id, 0, size() );
    }

    /**
	 * Performs a binary search for entry with
	 * {@link SuperVoxelMultisetEntry#getId()} <tt>id</tt> in the specified
	 * range. Note that you <b>must</b> @{link #sort sort} the list or the range
	 * before doing a search.
	 *
	 * @param id
	 *            the value to search for
	 * @param fromIndex
	 *            the lower boundary of the range (inclusive)
	 * @param toIndex
	 *            the upper boundary of the range (exclusive)
	 * @return the absolute offset in the list of the value, or its negative
	 *         insertion point into the sorted list.
	 */
    public int binarySearch( final long id, final int fromIndex, final int toIndex) {
        if ( fromIndex < 0 ) {
            throw new ArrayIndexOutOfBoundsException( fromIndex );
        }
        if ( toIndex > size() ) {
            throw new ArrayIndexOutOfBoundsException( toIndex );
        }

        int low = fromIndex;
        int high = toIndex - 1;

        while ( low <= high ) {
            final int mid = ( low + high ) >>> 1;
            final long midVal = get( mid ).getId();

            if ( midVal < id ) {
                low = mid + 1;
            }
            else if ( midVal > id ) {
                high = mid - 1;
            }
            else {
                return mid; // value found
            }
        }
        return -( low + 1 );  // value not found.
    }
}
