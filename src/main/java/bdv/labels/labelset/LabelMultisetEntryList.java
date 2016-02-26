package bdv.labels.labelset;


public class LabelMultisetEntryList
	extends MappedObjectArrayList< LabelMultisetEntry, LongMappedAccess >
{
	/**
	 * creates underlying data array
	 */
	public LabelMultisetEntryList( final int capacity )
	{
		super( LabelMultisetEntry.type, capacity );
	}

	/**
	 * doesn't create underlying data array
	 */
	protected LabelMultisetEntryList()
	{
		super( LabelMultisetEntry.type );
	}

	public LabelMultisetEntryList( final LongMappedAccessData data, final long baseOffset )
	{
		super( LabelMultisetEntry.type, data, baseOffset );
	}

	protected int multisetSize()
	{
		int size = 0;
		for ( final LabelMultisetEntry e : this )
			size += e.getCount();
		return size;
	}

	/**
	 * Performs a binary search for entry with
	 * {@link LabelMultisetEntry#getId()} <tt>id</tt> in the entire list.
	 * Note that you <b>must</b> {@link #sortById sort} the list before doing a
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
	 * {@link LabelMultisetEntry#getId()} <tt>id</tt> in the specified
	 * range. Note that you <b>must</b> {@link #sortById sort} the list or the range
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

        final LabelMultisetEntry ref = createRef();
        while ( low <= high ) {
            final int mid = ( low + high ) >>> 1;
            final long midVal = get( mid, ref ).getId();
            if ( midVal < id ) {
                low = mid + 1;
            }
            else if ( midVal > id ) {
                high = mid - 1;
            }
            else {
                releaseRef( ref );
                return mid; // value found
            }
        }
        releaseRef( ref );
        return -( low + 1 );  // value not found.
    }
}
