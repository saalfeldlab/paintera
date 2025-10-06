package org.janelia.saalfeldlab.paintera.id;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;


import java.util.stream.LongStream;

public class N5IdService implements IdService {

	private final N5Reader n5;

	private final String dataset;

	private long current;
	private long currentTemp = IdService.FIRST_TEMPORARY_ID;

	public N5IdService(final N5Reader n5, final String dataset, final long current) {

		super();
		this.n5 = n5;
		this.dataset = dataset;
		this.current = current;
	}

	public String getDataset() {

		return dataset;
	}

	@Override
	public synchronized void invalidate(final long id) {

		final long oldNext = current;
		current = IdService.max(current, id + 1);
		if (current != oldNext) {
			serializeMaxId();
		}
	}

	@Override
	public synchronized long current() {
		return current;
	}

	@Override
	public synchronized long next() {

		current++;
		serializeMaxId();
		return current;
	}

	@Override
	public synchronized long[] next(final int n) {

		final long[] ids = LongStream.range(1 + current, 1 + (current += n)).toArray();
		serializeMaxId();
		return ids;
	}

	@Override public long nextTemporary() {

		return ++currentTemp;
	}

	@Override public long[] nextTemporary(int n) {

		return LongStream.range(1 + currentTemp, 1 + (currentTemp += n)).toArray();
	}

	private void serializeMaxId() {
		if (n5 instanceof N5Writer) {
			((N5Writer)n5).setAttribute(dataset, "maxId", current);
		}
	}

	@Override
	public boolean isInvalidated(final long id) {

		return id < current;
	}

}
