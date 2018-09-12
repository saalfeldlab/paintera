package net.imglib2.transform.integer;

import net.imglib2.Localizable;
import net.imglib2.Positionable;
import net.imglib2.transform.InvertibleTransform;

public class PermuteCoordinateAxesTransform implements InvertibleTransform {

	private final int[] lookup;

	private final int[] inverseLookup;

	public PermuteCoordinateAxesTransform(int... lookup)
	{
		this.lookup = lookup;
		this.inverseLookup = new int[lookup.length];
		for (int index = 0; index < lookup.length; ++index)
			this.inverseLookup[lookup[index]] = index;
	}

	@Override
	public int numTargetDimensions() {
		return lookup.length;
	}

	@Override
	public int numSourceDimensions() {
		return lookup.length;
	}

	@Override
	public void apply(long[] source, long[] target) {
		for (int i = 0; i < lookup.length; ++i)
			target[i] = source[lookup[i]];
	}

	@Override
	public void apply(int[] source, int[] target) {
		for (int i = 0; i < lookup.length; ++i)
			target[i] = source[lookup[i]];
	}

	@Override
	public void apply(Localizable source, Positionable target) {
		for (int i = 0; i < lookup.length; ++i)
			target.setPosition(source.getLongPosition(lookup[i]), i);
	}

	@Override
	public PermuteCoordinateAxesTransform inverse() {
		return new PermuteCoordinateAxesTransform(inverseLookup.clone());
	}

	@Override
	public void applyInverse(long[] source, long[] target) {
		for (int i = 0; i < lookup.length; ++i)
			source[i] = target[inverseLookup[i]];
	}


	@Override
	public void applyInverse(int[] source, int[] target) {
		for (int i = 0; i < lookup.length; ++i)
			source[i] = target[inverseLookup[i]];
	}

	@Override
	public void applyInverse(Positionable source, Localizable target) {
		for (int i = 0; i < lookup.length; ++i)
			source.setPosition(target.getLongPosition(inverseLookup[i]), i);
	}


}
