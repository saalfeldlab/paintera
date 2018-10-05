package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.RandomAccessibleInterval;

public class Mask<D> {

	public final MaskInfo<D> info;

	public final RandomAccessibleInterval<D> mask;

	public Mask(MaskInfo<D> info, RandomAccessibleInterval<D> mask) {
		this.info = info;
		this.mask = mask;
	}
}
