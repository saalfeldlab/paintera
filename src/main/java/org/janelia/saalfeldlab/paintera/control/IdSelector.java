package org.janelia.saalfeldlab.paintera.control;

import bdv.viewer.Interpolation;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerState;
import org.janelia.saalfeldlab.fx.actions.MouseAction;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.VisitEveryDisplayPixel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

public class IdSelector {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<? extends IntegerType<?>, ?> source;

	private final SelectedIds selectedIds;

	private final Supplier<ViewerPanelFX> activeViewerSupplier;

	private final LongPredicate foregroundCheck;

	public IdSelector(
			final DataSource<? extends IntegerType<?>, ?> source,
			final SelectedIds selectedIds,
			final Supplier<ViewerPanelFX> activeViewerSupplier,
			final LongPredicate foregroundCheck) {

		super();
		this.source = source;
		this.selectedIds = selectedIds;
		this.activeViewerSupplier = activeViewerSupplier;
		this.foregroundCheck = foregroundCheck;
	}

	public MouseAction selectFragmentWithMaximumCountAction() {

		final var action = MouseAction.onAction(MouseEvent.MOUSE_CLICKED, event -> new SelectFragmentWithMaximumCount(!event.isAltDown()).accept(event));
		action.setName(SelectFragmentWithMaximumCount.class.getSimpleName());
		action.verifyButtonTrigger(MouseButton.PRIMARY);
		return action;

	}

	public MouseAction appendFragmentWithMaximumCountAction() {

		final MouseAction action = MouseAction.onAction(MouseEvent.MOUSE_CLICKED, event -> new AppendFragmentWithMaximumCount().accept(event));
		action.setName(AppendFragmentWithMaximumCount.class.getSimpleName());
		return action;
	}

	// TODO: use unique labels to collect all ids; caching
	public void selectAll() {

		final TLongSet allIds = new TLongHashSet();
		if (source.getDataType() instanceof LabelMultisetType)
			selectAllLabelMultisetType(allIds);
		else
			selectAllPrimitiveType(allIds);
		if (Thread.interrupted()) {
			LOG.debug("Select All Ids was Interrupted");
			allIds.clear();
			selectedIds.deactivateAll();
			return;
		}
		LOG.debug("Collected {} ids", allIds.size());
		selectedIds.activateAlso(allIds.toArray());
	}

	private void selectAllLabelMultisetType(final TLongSet allIds) {

		@SuppressWarnings("unchecked") final RandomAccessibleInterval<LabelMultisetType> data = (RandomAccessibleInterval<LabelMultisetType>)
				source.getDataSource(0, source.getNumMipmapLevels() - 1);

		final Cursor<LabelMultisetType> cursor = data.cursor();
		final var entry = new LabelMultisetEntry();
		while (cursor.hasNext()) {
			if (Thread.interrupted()) {
				return;
			}
			final LabelMultisetType lmt = cursor.next();
			for (Entry<Label> iterEntry : lmt.entrySetWithRef(entry)) {
				final long id = iterEntry.getElement().id();
				if (foregroundCheck.test(id))
					allIds.add(id);
			}
		}
	}

	private void selectAllPrimitiveType(final TLongSet allIds) {

		LOG.warn("Label data is stored as primitive type, looping over full resolution data to collect all ids -- SLOW");
		final RandomAccessibleInterval<? extends IntegerType<?>> data = source.getDataSource(0, 0);
		final Cursor<? extends IntegerType<?>> cursor = data.cursor();
		while (cursor.hasNext()) {
			if (Thread.interrupted()) {
				return;
			}
			final long id = cursor.next().getIntegerLong();
			if (foregroundCheck.test(id))
				allIds.add(id);
		}
	}

	public void selectAllInCurrentView(final ViewerPanelFX viewer) {

		final TLongSet idsInCurrentView = new TLongHashSet();
		if (source.getDataType() instanceof LabelMultisetType)
			selectAllInCurrentViewLabelMultisetType(viewer, idsInCurrentView);
		else
			selectAllInCurrentViewPrimitiveType(viewer, idsInCurrentView);
		LOG.debug("Collected {} ids in current view", idsInCurrentView.size());
		selectedIds.activate(idsInCurrentView.toArray());
	}

	@SuppressWarnings("unchecked")
	private void selectAllInCurrentViewLabelMultisetType(final ViewerPanelFX viewer, final TLongSet idsInCurrentView) {

		VisitEveryDisplayPixel.visitEveryDisplayPixel(
				(DataSource<LabelMultisetType, ?>)source,
				viewer,
				lmt -> {
					for (final Entry<Label> entry : lmt.entrySet()) {
						final long id = entry.getElement().id();
						if (foregroundCheck.test(id))
							idsInCurrentView.add(id);
					}
				}
		);
	}

	private void selectAllInCurrentViewPrimitiveType(final ViewerPanelFX viewer, final TLongSet idsInCurrentView) {

		VisitEveryDisplayPixel.visitEveryDisplayPixel(
				source,
				viewer,
				val -> {
					final long id = val.getIntegerLong();
					if (foregroundCheck.test(id))
						idsInCurrentView.add(id);
				}
		);
	}

	public void toggleLock(final FragmentSegmentAssignment assignment, final LockedSegments lock) {

		final long lastSelection = selectedIds.getLastSelection();
		if (!Label.regular(lastSelection))
			return;

		final long segment = assignment.getSegment(lastSelection);
		if (lock.isLocked(segment))
			lock.unlock(segment);
		else
			lock.lock(segment);
	}

	private abstract class SelectMaximumCount implements Consumer<MouseEvent> {

		@Override
		public void accept(final MouseEvent e) {

			final var activeViewer = activeViewerSupplier.get();
			if (activeViewer == null)
				return;

			final AffineTransform3D affine = new AffineTransform3D();
			final ViewerState viewerState = activeViewer.getState().copy();
			viewerState.getViewerTransform(affine);
			final AffineTransform3D screenScaleTransform = new AffineTransform3D();
			activeViewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
			final int level = viewerState.getBestMipMapLevel(screenScaleTransform, source);

			source.getSourceTransform(0, level, affine);
			final var interpolatedDataSource = source.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR);
			final var access = RealViews.transformReal(interpolatedDataSource, affine).realRandomAccess();
			activeViewer.getMouseCoordinates(access);
			access.setPosition(0L, 2);
			activeViewer.displayToGlobalCoordinates(access);
			final IntegerType<?> val = access.get();
			final long id = val.getIntegerLong();
			actOn(id);
		}

		protected abstract void actOn(final long id);
	}

	private class SelectFragmentWithMaximumCount extends SelectMaximumCount {

		private final boolean foregroundOnly;

		public SelectFragmentWithMaximumCount(boolean foregroundOnly) {

			this.foregroundOnly = foregroundOnly;
		}

		@Override
		protected void actOn(final long id) {

			if (!foregroundOnly || foregroundCheck.test(id)) {
				if (selectedIds.isOnlyActiveId(id)) {
					selectedIds.deactivate(id);
				} else {
					selectedIds.activate(id);
				}
			}
		}
	}

	private class AppendFragmentWithMaximumCount extends SelectMaximumCount {

		@Override
		protected void actOn(final long id) {

			if (foregroundCheck.test(id)) {
				if (selectedIds.isActive(id)) {
					selectedIds.deactivate(id);
				} else {
					selectedIds.activateAlso(id);
				}
			}
		}
	}

}
