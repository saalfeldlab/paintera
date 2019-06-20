package org.janelia.saalfeldlab.paintera.state;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Interpolation;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.label.Label;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class LabelSourceStateMergeDetachHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<? extends IntegerType<?>, ?> source;

	private final SelectedIds selectedIds;

	private final FragmentSegmentAssignment assignment;

	private final IdService idService;

	private final HashMap<ViewerPanelFX, EventHandler<Event>> handlers = new HashMap<>();

	public LabelSourceStateMergeDetachHandler(
			final DataSource<? extends IntegerType<?>, ?> source,
			final SelectedIds selectedIds,
			final FragmentSegmentAssignment assignment,
			final IdService idService) {
		this.source = source;
		this.selectedIds = selectedIds;
		this.assignment = assignment;
		this.idService = idService;
	}

	public EventHandler<Event> viewerHandler(final PainteraBaseView paintera, final KeyTracker keyTracker) {
		return event -> {
			final EventTarget target = event.getTarget();
			if (!(target instanceof Node))
				return;
			Node node = (Node) target;
			LOG.trace("Handling event {} in target {}", event, target);
			// kind of hacky way to accomplish this:
			while (node != null) {
				if (node instanceof ViewerPanelFX) {
					handlers.computeIfAbsent((ViewerPanelFX) node, k -> this.makeHandler(paintera, keyTracker, k)).handle(event);
					return;
				}
				node = node.getParent();
			}
		};
	}

	private EventHandler<Event> makeHandler(final PainteraBaseView paintera, final KeyTracker keyTracker, final ViewerPanelFX vp) {
		final DelegateEventHandlers.AnyHandler handler = DelegateEventHandlers.handleAny();
		handler.addOnMousePressed(EventFX.MOUSE_PRESSED(
				"merge fragments",
				new MergeFragments(vp),
				e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Merge) && e.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT)));
		handler.addOnMousePressed(EventFX.MOUSE_PRESSED(
				"detach fragment",
				new DetachFragment(vp),
				e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Split) && e.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT)));
		handler.addOnMousePressed(EventFX.MOUSE_PRESSED(
				"detach fragment",
				new ConfirmSelection(vp),
				e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Split) && e.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT, KeyCode.CONTROL)));
		return handler;
	}

	private class MergeFragments implements Consumer<MouseEvent> {

		private final ViewerPanelFX viewer;

		private MergeFragments(final ViewerPanelFX viewer) {
			this.viewer = viewer;
		}

		@Override
		public void accept(final MouseEvent e)
		{
			synchronized (viewer)
			{

				final long lastSelection = selectedIds.getLastSelection();

				if (lastSelection == Label.INVALID) { return; }

				final AffineTransform3D viewerTransform = new AffineTransform3D();
				final ViewerState viewerState     = viewer.getState();
				viewerState.getViewerTransform(viewerTransform);
				final int level = viewerState.getBestMipMapLevel(viewerTransform, source);

				final AffineTransform3D affine = new AffineTransform3D();
				source.getSourceTransform(0, level, affine);
				final RealRandomAccess<? extends IntegerType<?>> access = RealViews.transformReal(
						source.getInterpolatedDataSource(
								0,
								level,
								Interpolation.NEARESTNEIGHBOR),
						affine).realRandomAccess();
				viewer.getMouseCoordinates(access);
				access.setPosition(0L, 2);
				viewer.displayToGlobalCoordinates(access);
				final IntegerType<?> val = access.get();
				final long id = val.getIntegerLong();

				LOG.debug("Merging fragments: {} -- last selection: {}", id, lastSelection);
				final Optional<Merge> action = assignment.getMergeAction(
						id,
						lastSelection,
						idService::next);
				action.ifPresent(assignment::apply);
			}
		}

	}

	private class DetachFragment implements Consumer<MouseEvent>
	{
		private final ViewerPanelFX viewer;

		private DetachFragment(final ViewerPanelFX viewer) {
			this.viewer = viewer;
		}

		@Override
		public void accept(final MouseEvent e)
		{
			final long lastSelection = selectedIds.getLastSelection();

			if (lastSelection == Label.INVALID) { return; }

			final AffineTransform3D viewerTransform = new AffineTransform3D();

			final ViewerState       viewerState     = viewer.getState();
			viewerState.getViewerTransform(viewerTransform);
			final int level = viewerState.getBestMipMapLevel(viewerTransform, source);

			final AffineTransform3D affine = new AffineTransform3D();
			source.getSourceTransform(0, level, affine);
			final RealRandomAccessible<? extends IntegerType<?>> transformedSource = RealViews
					.transformReal(
							source.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR),
							affine);
			final RealRandomAccess<? extends IntegerType<?>> access = transformedSource.realRandomAccess();
			viewer.getMouseCoordinates(access);
			access.setPosition(0L, 2);
			viewer.displayToGlobalCoordinates(access);
			final IntegerType<?> val = access.get();
			final long id  = val.getIntegerLong();

			final Optional<Detach> detach = assignment.getDetachAction(id, lastSelection);
			detach.ifPresent(assignment::apply);
		}

	}

	private class ConfirmSelection implements Consumer<MouseEvent>
	{

		private final ViewerPanelFX viewer;

		private ConfirmSelection(final ViewerPanelFX viewer) {
			this.viewer = viewer;
		}

		@Override
		public void accept(final MouseEvent e)
		{
						final long[] activeFragments = selectedIds.getActiveIds();
						final long[] activeSegments  = Arrays.stream(activeFragments).map(assignment::getSegment).toArray();

						if (activeSegments.length > 1)
						{
							LOG.info("More than one segment active, not doing anything!");
							return;
						}

						if (activeSegments.length == 0)
						{
							LOG.info("No segments active, not doing anything!");
							return;
						}

						final AffineTransform3D viewerTransform = new AffineTransform3D();
						final ViewerState       viewerState     = viewer.getState().copy();
							viewerState.getViewerTransform(viewerTransform);
							final int level = viewerState.getBestMipMapLevel(viewerTransform, source);

						final AffineTransform3D affine = new AffineTransform3D();
						source.getSourceTransform(0, level, affine);
						final RealRandomAccessible<? extends IntegerType<?>> transformedSource = RealViews
								.transformReal(
										source.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR),
										affine);
						final RealRandomAccess<? extends IntegerType<?>> access = transformedSource.realRandomAccess();
						viewer.getMouseCoordinates(access);
						access.setPosition(0L, 2);
						viewer.displayToGlobalCoordinates(access);
						final IntegerType<?> val = access.get();
						final long         selectedFragment    = val.getIntegerLong();
						final long         selectedSegment     = assignment.getSegment(selectedFragment);
						final TLongHashSet selectedSegmentsSet = new TLongHashSet(new long[] {selectedSegment});
						final TLongHashSet visibleFragmentsSet = new TLongHashSet();

						if (activeSegments[0] == selectedSegment)
						{
							LOG.debug("confirm merge and separate of single segment");
							visitEveryDisplayPixel(
									source,
									viewer,
									obj -> visibleFragmentsSet.add(obj.getIntegerLong()));
							final long[] visibleFragments            = visibleFragmentsSet.toArray();
							final long[] fragmentsInActiveSegment    = Arrays.stream(visibleFragments).filter(frag -> selectedSegmentsSet.contains(assignment.getSegment(frag))).toArray();
							final long[] fragmentsNotInActiveSegment = Arrays.stream(visibleFragments).filter(frag -> !selectedSegmentsSet.contains(assignment.getSegment(frag))).toArray();

							final Optional<AssignmentAction> action = assignment.getConfirmGroupingAction(fragmentsInActiveSegment, fragmentsNotInActiveSegment);
							action.ifPresent(assignment::apply);
						}

						else
						{
							LOG.debug("confirm merge and separate of two segments");
							final long[]                           relevantSegments   = new long[] {activeSegments[0],
									selectedSegment};
							final TLongObjectHashMap<TLongHashSet> fragmentsBySegment = new TLongObjectHashMap<>();
							Arrays.stream(relevantSegments).forEach(seg -> fragmentsBySegment.put(
									seg,
									new TLongHashSet()));
							visitEveryDisplayPixel(source, viewer, obj -> {
								final long         frag  = obj.getIntegerLong();
								final TLongHashSet frags = fragmentsBySegment.get(assignment.getSegment(frag));
								if (frags != null)
								{
									frags.add(frag);
								}
							});
							final Optional<AssignmentAction> action = assignment.getConfirmTwoSegmentsAction(
									fragmentsBySegment.get(relevantSegments[0]).toArray(),
									fragmentsBySegment.get(relevantSegments[1]).toArray());
							action.ifPresent(assignment::apply);
						}

		}
	}

	private static <I> void visitEveryDisplayPixel(
			final DataSource<I, ?> dataSource,
			final ViewerPanelFX viewer,
			final Consumer<I> doAtPixel)
	{
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		final ViewerState       state           = viewer.getState().copy();
		state.getViewerTransform(viewerTransform);
		final int level = state.getBestMipMapLevel(viewerTransform, dataSource);

		dataSource.getSourceTransform(0, level, sourceTransform);

		final RealRandomAccessible<I>                                    interpolatedSource = dataSource.getInterpolatedDataSource(
				0,
				level,
				Interpolation.NEARESTNEIGHBOR);

		final RealTransformRealRandomAccessible<I, InverseRealTransform> transformedSource  = RealViews.transformReal(
				interpolatedSource,
				sourceTransform);

		final int w = (int) viewer.getWidth();
		final int h = (int) viewer.getHeight();
		final IntervalView<I> screenLabels =
				Views.interval(
						Views.hyperSlice(
								RealViews.affine(transformedSource, viewerTransform), 2, 0),
						new FinalInterval(w, h));

		visitEveryPixel(screenLabels, doAtPixel);
	}

	private static <I> void visitEveryPixel(
			final RandomAccessibleInterval<I> img,
			final Consumer<I> doAtPixel)
	{
		final Cursor<I> cursor = Views.flatIterable(img).cursor();
		while (cursor.hasNext())
			doAtPixel.accept(cursor.next());
	}

}
