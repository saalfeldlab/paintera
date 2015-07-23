package bdv.labels.labelset;

import java.util.Iterator;
import java.util.List;

public interface RefList< O > extends List< O >
{
	public static interface RefIterator< O > extends Iterator< O >
	{
		public void release();

		public void reset();
	}

	public O createRef();

	public void releaseRef( final O ref );

	public O get( final int index, final O ref );

	@Override
	public RefIterator< O > iterator();
}
