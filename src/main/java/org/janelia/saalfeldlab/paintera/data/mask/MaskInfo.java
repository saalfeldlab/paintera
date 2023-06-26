package org.janelia.saalfeldlab.paintera.data.mask;

public class MaskInfo {

	public final int time;

	public final int level;

	public MaskInfo(final int time, final int level) {

		super();
		this.time = time;
		this.level = level;
	}

	public MaskInfo copy() {

		return new MaskInfo(time, level);
	}

	@Override
	public String toString() {

		return String.format("{time=%d, level=%d}", time, level);
	}
}
