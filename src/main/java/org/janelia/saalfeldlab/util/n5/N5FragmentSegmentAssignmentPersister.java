package org.janelia.saalfeldlab.util.n5;

import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.LongArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.UnableToPersist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class N5FragmentSegmentAssignmentPersister implements FragmentSegmentAssignmentOnlyLocal.Persister {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final N5Writer writer;

	private final String dataset;

	public N5FragmentSegmentAssignmentPersister(N5Writer writer, String dataset) {
		this.writer = writer;
		this.dataset = dataset;
		LOG.debug("Creating {} with writer {} and dataset {}", getClass().getName(), this.writer, this.dataset);
	}

	@Override
	public void persist(long[] keys, long[] values) throws UnableToPersist {
		try
		{

			LOG.debug("Persisting fragment-segment-lookup: {} {}", keys, values);

			final DatasetAttributes attrs = new DatasetAttributes(
					new long[] {keys.length, 2},
					new int[] {Math.max(keys.length, 1), 1},
					DataType.UINT64,
					new GzipCompression()
			);
			writer.createDataset(dataset, attrs);

			if (keys.length == 0)
			{
				LOG.debug("Zero-length-lookup: Will not write any data.");
			}

			final DataBlock<long[]> keyBlock = new LongArrayDataBlock(
					new int[] {keys.length, 1},
					new long[] {0, 0},
					keys
			);
			final DataBlock<long[]> valueBlock = new LongArrayDataBlock(
					new int[] {values.length, 1},
					new long[] {0, 1},
					values
			);
			writer.writeBlock(dataset, attrs, keyBlock);
			writer.writeBlock(dataset, attrs, valueBlock);
		} catch (final Exception e)
		{
			throw new UnableToPersist(e);
		}
	}

}
