package org.janelia.saalfeldlab.paintera.cache;

import java.lang.ref.SoftReference;
import java.util.Optional;
import java.util.function.Consumer;

public class SoftRef<V> extends SoftReference<V> {

	private final Object synchronizeObject;

	private final Consumer<V> onConstruction;

	private final Consumer<V> onClear;

	public SoftRef(V v, final Consumer<V> onConstruction, final Consumer<V> onClear)
	{
		this(v, onConstruction, onClear, Optional.empty());
	}

	public SoftRef(V v, final Consumer<V> onConstruction, final Consumer<V> onClear, final Object synchronizeObject)
	{
		this(v, onConstruction, onClear, Optional.ofNullable(synchronizeObject));
	}

	public SoftRef(V v, final Consumer<V> onConstruction, final Consumer<V> onClear, final Optional<Object> synchronizeObject) {
		super(v);
		this.onConstruction = onConstruction;
		this.onClear = onClear;
		this.synchronizeObject = synchronizeObject.orElse(this);
		synchronized (synchronizeObject) {
			onConstruction.accept(v);
//			currentSizeInBytes += memoryUsageInBytes.applyAsLong(v);
		}
	}

	@Override
	public void clear() {
		V v = get();
		if (v != null)
			synchronized (synchronizeObject) {
			onClear.accept(v);
//				currentSizeInBytes -= memoryUsageInBytes.applyAsLong(v);
			}
		super.clear();
	}

}
