package bdv.labels.labelset;

import java.util.Collection;
import java.util.Set;

/**
 * minimal subset of Guava Multiset interface.
 */
public interface Multiset< E > extends Collection< E >
{
	public int count( Object element );

//	public Set< E > elementSet();

	public Set< Entry< E > > entrySet();

	public interface Entry< E >
	{
		public E getElement();

		public int getCount();
	}
}
