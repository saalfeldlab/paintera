package bdv.bigcat.viewer.util;

import java.util.Map;
import java.util.function.Supplier;

public class Maps
{

	public static < K, V > V getOrDefaultFromSupplier( final Map< K, V > map, final K key, final Supplier< V > defaultValue )
	{
		final V value = map.get( key );
		if ( value == null )
		{
			final V v = defaultValue.get();
			map.put( key, v );
			return v;
		}
		else
			return value;
	}

	public static < K, V > V getOrDefault( final Map< K, V > map, final K key, final V defaultValue )
	{
		return getOrDefaultFromSupplier( map, key, ( Supplier< V > ) () -> defaultValue );
	}
}
