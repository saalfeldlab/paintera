package org.janelia.saalfeldlab.paintera.ui.dialogs.open;

import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.util.Intervals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class VolatileHelpers {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class CreateInvalidVolatileLabelMultisetArray
			implements CreateInvalid<Long, Cell<VolatileLabelMultisetArray>> {

		private final CellGrid grid;

		public CreateInvalidVolatileLabelMultisetArray(final CellGrid grid) {

			super();
			this.grid = grid;
		}

		@Override
		public Cell<VolatileLabelMultisetArray> createInvalid(final Long key) throws Exception {

			final long[] cellPosition = new long[grid.numDimensions()];
			grid.getCellGridPositionFlat(key, cellPosition);
			final long[] cellMin = new long[cellPosition.length];
			final int[] cellDims = new int[cellPosition.length];
			grid.getCellDimensions(cellPosition, cellMin, cellDims);

			return new Cell<>(cellDims, cellMin, newEmptyAccess((int)Intervals.numElements(cellDims), false));
		}
	}

	public static VolatileLabelMultisetArray newEmptyAccess(int numElements, boolean isValid) {

		long[] argMaxes = new long[numElements];
		Arrays.fill(argMaxes, Label.INVALID);
		return new VolatileLabelMultisetArray(numElements, isValid, argMaxes);
	}

}
