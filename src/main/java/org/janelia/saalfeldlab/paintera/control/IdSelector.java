package org.janelia.saalfeldlab.paintera.control;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.janelia.saalfeldlab.fx.event.MouseClickFX;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Interpolation;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.scene.input.MouseEvent;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;

public class IdSelector
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<? extends IntegerType<?>, ?> source;

	private final SelectedIds selectedIds;

	private final ViewerPanelFX viewer;

	public IdSelector(
			final DataSource<? extends IntegerType<?>, ?> source,
			final SelectedIds selectedIds,
			final ViewerPanelFX viewer)
	{
		super();
		this.source = source;
		this.selectedIds = selectedIds;
		this.viewer = viewer;
	}

	public MouseClickFX selectFragmentWithMaximumCount(final String name, final Predicate<MouseEvent> eventFilter)
	{
		return new MouseClickFX(name, new SelectFragmentWithMaximumCount(), eventFilter);
	}

	public MouseClickFX appendFragmentWithMaximumCount(final String name, final Predicate<MouseEvent> eventFilter)
	{
		return new MouseClickFX(name, new AppendFragmentWithMaximumCount(), eventFilter);
	}

	public void selectAll()
	{
		final IntegerType<?> dataType = source.getDataType();
		if (dataType instanceof LabelMultisetType)
		{
			selectAllLabelMultisetType();
		}
	}

	private void selectAllLabelMultisetType()
	{
		new Thread(() -> {
			@SuppressWarnings("unchecked")
			final RandomAccessibleInterval<LabelMultisetType> data = (RandomAccessibleInterval<LabelMultisetType>)
					source.getDataSource(0, source.getNumMipmapLevels() - 1);

			final TLongSet allIds = new TLongHashSet();
			final Cursor<LabelMultisetType> cursor = Views.iterable(data).cursor();
			while (cursor.hasNext())
			{
				final LabelMultisetType lmt = cursor.next();
				for (final Entry<Label> entry : lmt.entrySet())
					allIds.add(entry.getElement().id());
			}
			LOG.info("Collected {} ids", allIds.size());
			InvokeOnJavaFXApplicationThread.invoke(() -> selectedIds.activate(allIds.toArray()));
		}).start();
	}

	public void toggleLock(final FragmentSegmentAssignment assignment, final LockedSegments lock)
	{
			final long lastSelection = selectedIds.getLastSelection();
			if (!Label.regular(lastSelection))
				return;

			final long segment = assignment.getSegment(lastSelection);
			if (lock.isLocked(segment))
				lock.unlock(segment);
			else
				lock.lock(segment);
	}

	private abstract class SelectMaximumCount implements Consumer<MouseEvent>
	{

		@Override
		public void accept(final MouseEvent e)
		{
				final AffineTransform3D affine      = new AffineTransform3D();
				final ViewerState       viewerState = viewer.getState().copy();
				viewerState.getViewerTransform(affine);
				final AffineTransform3D screenScaleTransforms = new AffineTransform3D();
				final int level = viewerState.getBestMipMapLevel(screenScaleTransforms, source);

				source.getSourceTransform(0, level, affine);
				final RealRandomAccess<? extends IntegerType<?>> access =
						RealViews.transformReal(source.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR), affine).realRandomAccess();
				viewer.getMouseCoordinates(access);
				access.setPosition(0L, 2);
				viewer.displayToGlobalCoordinates(access);
				final IntegerType<?> val = access.get();
				final long id  = val.getIntegerLong();
				actOn(id);
		}

		protected abstract void actOn(final long id);
	}

	private class SelectFragmentWithMaximumCount extends SelectMaximumCount
	{

		@Override
		protected void actOn(final long id)
		{
			if (Label.regular(id))
			{
				if (selectedIds.isOnlyActiveId(id))
				{
					selectedIds.deactivate(id);
				}
				else
				{
					selectedIds.activate(id);
				}
			}
		}
	}

	private class AppendFragmentWithMaximumCount extends SelectMaximumCount
	{

		@Override
		protected void actOn(final long id)
		{
			if (Label.regular(id))
			{
				if (selectedIds.isActive(id))
				{
					selectedIds.deactivate(id);
				}
				else
				{
					selectedIds.activateAlso(id);
				}
			}
		}
	}

}
