package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.lang.invoke.MethodHandles;

import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetEntryList;
import net.imglib2.type.label.LongMappedAccessData;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.util.Intervals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VolatileHelpers
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class CreateInvalidVolatileLabelMultisetArray
			implements CreateInvalid<Long, Cell<VolatileLabelMultisetArray>>
	{

		private final CellGrid grid;

		public CreateInvalidVolatileLabelMultisetArray(final CellGrid grid)
		{
			super();
			this.grid = grid;
		}

		@Override
		public Cell<VolatileLabelMultisetArray> createInvalid(final Long key) throws Exception
		{
			final long[] cellPosition = new long[grid.numDimensions()];
			grid.getCellGridPositionFlat(key, cellPosition);
			final long[] cellMin  = new long[cellPosition.length];
			final int[]  cellDims = new int[cellPosition.length];
			grid.getCellDimensions(cellPosition, cellMin, cellDims);

			final LabelMultisetEntry e           = new LabelMultisetEntry(Label.INVALID, 1);
			final int                numEntities = (int) Intervals.numElements(cellDims);

			final LongMappedAccessData   listData = LongMappedAccessData.factory.createStorage(32);
			final LabelMultisetEntryList list     = new LabelMultisetEntryList(listData, 0);
			list.createListAt(listData, 0);
			list.add(e);
			final int[]                      data  = new int[numEntities];
			final VolatileLabelMultisetArray array = new VolatileLabelMultisetArray(
					data,
					listData,
					false,
					new long[] {Label.INVALID}
			);
			return new Cell<>(cellDims, cellMin, array);
		}

	}

}
