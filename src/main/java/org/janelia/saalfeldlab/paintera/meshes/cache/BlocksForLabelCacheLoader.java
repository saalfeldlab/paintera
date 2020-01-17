package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.Interval;
import net.imglib2.cache.CacheLoader;
import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public class BlocksForLabelCacheLoader<T> implements CacheLoader<T, Interval[]>, Interruptible<T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Function<T, Interval[]> blocksForLabel;

	private final List<Consumer<T>> interruptionListeners = new ArrayList<>();

	public BlocksForLabelCacheLoader(final Function<T, Interval[]> blocksForLabel)
	{
		this.blocksForLabel = blocksForLabel;
	}

	@Override
	public Interval[] get(final T key)
	{
		final AtomicBoolean isInterrupted = new AtomicBoolean();
		final Consumer<T> listener = interruptedKey -> {
			if (interruptedKey.equals(key))
				isInterrupted.set(true);
		};
		synchronized (this.interruptionListeners)
		{
			this.interruptionListeners.add(listener);
		}

		try
		{
			final Interval[] ret = blocksForLabel.apply(key);
			return isInterrupted.get() ? null : ret;
		}
		finally
		{
			synchronized (this.interruptionListeners)
			{
				this.interruptionListeners.remove(listener);
			}
		}
	}

	@Override
	public void interruptFor(final T t)
	{
		synchronized (this.interruptionListeners)
		{
			this.interruptionListeners.forEach(l -> l.accept(t));
		}
	}

}
