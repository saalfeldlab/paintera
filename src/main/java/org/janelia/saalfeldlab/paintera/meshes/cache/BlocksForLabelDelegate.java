package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.Interval;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class BlocksForLabelDelegate<T, U> implements InterruptibleFunction<T, Interval[]>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final InterruptibleFunction<U, Interval[]> delegate;

	private final Function<T, U[]> keyMapping;

	public BlocksForLabelDelegate(
			final InterruptibleFunction<U, Interval[]> delegate,
			final Function<T, U[]> keyMapping)
	{
		super();
		this.delegate = delegate;
		this.keyMapping = keyMapping;
	}

	@Override
	public Interval[] apply(final T t) {
		final Set<HashWrapper<Interval>> intervals = new HashSet<>();

		final U[] mappedKeys = this.keyMapping.apply(t);
		LOG.debug("Mapped keys from {} to {}", t, mappedKeys);

		for (final U key : mappedKeys)
		{
			final Interval[] res = delegate.apply(key);
			Arrays.stream(res).map(HashWrapper::interval).forEach(intervals::add);
		}

		LOG.debug("Got intervals: {}", intervals);
		return intervals.stream().map(HashWrapper::getData).toArray(Interval[]::new);
	}

	@Override
	public void interruptFor(final T t)
	{
		LOG.debug("Interrupting for {}", t);
		Arrays.stream(keyMapping.apply(t)).forEach(delegate::interruptFor);
	}

	@SuppressWarnings("unchecked")
	public static <T, U> BlocksForLabelDelegate<T, U>[] delegate(
			final InterruptibleFunction<U, Interval[]>[] delegates,
			final Function<T, U[]> keyMapping)
	{
		return Arrays
				.stream(delegates)
				.map(d -> new BlocksForLabelDelegate<>(d, keyMapping))
				.toArray(BlocksForLabelDelegate[]::new);
	}
}
