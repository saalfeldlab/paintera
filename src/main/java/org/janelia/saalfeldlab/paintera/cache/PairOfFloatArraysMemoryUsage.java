package org.janelia.saalfeldlab.paintera.cache;

import net.imglib2.util.Pair;
import org.scijava.plugin.Plugin;

@Plugin(type = DiscoverableMemoryUsage.class)
public class PairOfFloatArraysMemoryUsage implements DiscoverableMemoryUsage<Pair<float[], float[]>> {


	@Override
	public boolean isApplicable(Object object) {
		if (object instanceof Pair<?, ?>)
		{
			Pair<?, ?> p = (Pair<?, ?>) object;
			return p.getA() instanceof float[] && p.getB() instanceof float[];
		}
		return false;
	}

	@Override
	public long applyAsLong(Pair<float[], float[]> pair) {
		return pair.getA().length * Float.BYTES + pair.getB().length * Float.BYTES;
	}
}
