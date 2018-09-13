package org.janelia.saalfeldlab.paintera.cache;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public interface Invalidate<K> extends InvalidateAll {

	Collection<K> invalidateMatching(Predicate<K> test);

	void invalidate(Collection<K> keys);

	default void invalidate(K... keys) {
		invalidate(Arrays.asList(keys));
	}

	void invalidate(K key);

}
