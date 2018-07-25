package org.janelia.saalfeldlab.paintera.control;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import javafx.scene.Node;
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
import org.janelia.saalfeldlab.fx.event.InstallAndRemove;
import org.janelia.saalfeldlab.fx.event.MouseClickFX;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdSelector
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	public IdSelector(
			final ViewerPanelFX viewer,
			final SourceInfo sourceInfo)
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
	}

	public InstallAndRemove<Node> selectFragmentWithMaximumCount(final String name, final Predicate<MouseEvent>
			eventFilter)
	{
		final SelectFragmentWithMaximumCount selectFragment = new SelectFragmentWithMaximumCount();
		return new MouseClickFX(name, selectFragment::click, eventFilter);
	}

	public InstallAndRemove<Node> appendFragmentWithMaximumCount(final String name, final Predicate<MouseEvent>
			eventFilter)
	{
		final AppendFragmentWithMaximumCount appendFragment = new AppendFragmentWithMaximumCount();
		return new MouseClickFX(name, appendFragment::click, eventFilter);
	}

	public InstallAndRemove<Node> merge(final String name, final Predicate<MouseEvent> eventFilter)
	{
		final MergeFragments merge = new MergeFragments();
		return new MouseClickFX(name, merge::click, eventFilter);
	}

	public InstallAndRemove<Node> detach(final String name, final Predicate<MouseEvent> eventFilter)
	{
		final DetachFragment detach = new DetachFragment();
		return new MouseClickFX(name, detach::click, eventFilter);
	}

	public InstallAndRemove<Node> confirm(final String name, final Predicate<MouseEvent> eventFilter)
	{
		final ConfirmSelection confirmSelection = new ConfirmSelection();
		return new MouseClickFX(name, confirmSelection::click, eventFilter);
	}

	public void toggleLock()
	{
		final SourceState<?, ?> currentState = sourceInfo.currentState().get();
		if (currentState != null && currentState instanceof LabelSourceState<?, ?>)
		{
			final LabelSourceState<?, ?> state         = (LabelSourceState<?, ?>) currentState;
			final long                   lastSelection = state.selectedIds().getLastSelection();

			if (!Label.regular(lastSelection)) { return; }

			final long                segment = state.assignment().getSegment(lastSelection);
			final LockedSegmentsState lock    = state.lockedSegments();
			if (lock.isLocked(segment))
			{
				lock.unlock(segment);
			}
			else
			{
				lock.lock(segment);
			}
		}
	}

	private abstract class SelectMaximumCount
	{

		public <I extends IntegerType<I>> void click(final MouseEvent e)
		{
			final Optional<Source<?>> optionalSource = getCurrentSource();
			if (!optionalSource.isPresent()) { return; }
			final Source<?> source = optionalSource.get();
			if (source instanceof DataSource<?, ?>)
			{
				@SuppressWarnings("unchecked") final DataSource<I, ?> dataSource = (DataSource<I, ?>) source;
				final SourceState<?, ?> currentSourceState = sourceInfo.getState(source);
				if (!(currentSourceState instanceof LabelSourceState<?, ?>))
				{
					LOG.warn("Not a label source -- cannot select  id.");
					return;
				}
				@SuppressWarnings("unchecked") final LabelSourceState<I, ?> state = (LabelSourceState<I, ?>)
						currentSourceState;
				final Optional<SelectedIds> selectedIds = Optional.ofNullable(state.selectedIds());
				if (selectedIds.isPresent())
				{
					synchronized (viewer)
					{
						final AffineTransform3D affine      = new AffineTransform3D();
						final ViewerState       viewerState = viewer.getState();
						viewerState.getViewerTransform(affine);
						final AffineTransform3D screenScaleTransforms = new AffineTransform3D();
						final int               level                 = viewerState.getBestMipMapLevel(
								screenScaleTransforms,
								getIndexOf(dataSource, viewerState)
						                                                                              );
						dataSource.getSourceTransform(0, level, affine);
						final RealTransformRealRandomAccessible<I, InverseRealTransform>.RealTransformRealRandomAccess
								access = RealViews.transformReal(
								dataSource.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR),
								affine
						                                                                                                                               ).realRandomAccess();
						viewer.getMouseCoordinates(access);
						access.setPosition(0l, 2);
						viewer.displayToGlobalCoordinates(access);
						final I    val = access.get();
						final long id  = val.getIntegerLong();
						actOn(id, selectedIds.get());
					}
				}
			}
		}

		protected abstract void actOn(final long id, SelectedIds selectedIds);
	}

	private class SelectFragmentWithMaximumCount extends SelectMaximumCount
	{

		@Override
		protected void actOn(final long id, final SelectedIds selectedIds)
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
		protected void actOn(final long id, final SelectedIds selectedIds)
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

	private class MergeFragments
	{

		public <I extends IntegerType<I>> void click(final MouseEvent e)
		{
			final Optional<Source<?>> optionalSource = getCurrentSource();
			if (!optionalSource.isPresent()) { return; }
			final Source<?> source = optionalSource.get();
			if (source instanceof DataSource<?, ?>)
			{
				@SuppressWarnings("unchecked") final DataSource<I, ?> dataSource = (DataSource<I, ?>) source;
				final SourceState<?, ?> currentSourceState = sourceInfo.getState(source);
				if (!(currentSourceState instanceof LabelSourceState<?, ?>))
				{
					LOG.warn("Not a label source -- cannot select  id.");
					return;
				}
				@SuppressWarnings("unchecked") final LabelSourceState<I, ?> state = (LabelSourceState<I, ?>)
						currentSourceState;
				final Optional<SelectedIds>                    selectedIds        = Optional.ofNullable(state
						.selectedIds());
				final Optional<FragmentSegmentAssignmentState> assignmentOptional = Optional.ofNullable(state
						.assignment());
				if (selectedIds.isPresent() && assignmentOptional.isPresent())
				{
					synchronized (viewer)
					{
						final FragmentSegmentAssignmentState assignments = assignmentOptional.get();

						final long lastSelection = selectedIds.get().getLastSelection();

						if (lastSelection == Label.INVALID) { return; }

						final AffineTransform3D viewerTransform = new AffineTransform3D();
						final ViewerState       viewerState     = viewer.getState();
						viewerState.getViewerTransform(viewerTransform);
						final int               level  = viewerState.getBestMipMapLevel(
								viewerTransform,
								getIndexOf(source, viewerState)
						                                                               );
						final AffineTransform3D affine = new AffineTransform3D();
						dataSource.getSourceTransform(0, level, affine);
						final RealRandomAccess<I> access = RealViews.transformReal(
								dataSource.getInterpolatedDataSource(
										0,
										level,
										Interpolation.NEARESTNEIGHBOR
								                                    ),
								affine
						                                                          ).realRandomAccess();
						viewer.getMouseCoordinates(access);
						access.setPosition(0l, 2);
						viewer.displayToGlobalCoordinates(access);
						final I    val = access.get();
						final long id  = val.getIntegerLong();

						LOG.debug("Merging fragments: {} -- last selection: {}", id, lastSelection);
						final Optional<Merge> action = assignments.getMergeAction(
								id,
								lastSelection,
								state.idService()::next
						                                                         );
						action.ifPresent(assignments::apply);
					}
				}
			}
		}

	}

	private class DetachFragment
	{

		public <I extends IntegerType<I>> void click(final MouseEvent e)
		{
			final Optional<Source<?>> optionalSource = getCurrentSource();
			if (!optionalSource.isPresent()) { return; }
			final Source<?> source = optionalSource.get();
			if (source instanceof DataSource<?, ?>)
			{
				@SuppressWarnings("unchecked") final DataSource<I, ?> dataSource = (DataSource<I, ?>) source;
				final SourceState<?, ?> currentSourceState = sourceInfo.getState(source);
				if (!(currentSourceState instanceof LabelSourceState<?, ?>))
				{
					LOG.warn("Not a label source -- cannot select  id.");
					return;
				}
				@SuppressWarnings("unchecked") final LabelSourceState<I, ?> state = (LabelSourceState<I, ?>)
						currentSourceState;
				final Optional<SelectedIds>                    selectedIds        = Optional.ofNullable(state
						.selectedIds());
				final Optional<FragmentSegmentAssignmentState> assignmentOptional = Optional.ofNullable(state
						.assignment());
				if (selectedIds.isPresent() && assignmentOptional.isPresent())
				{
					synchronized (viewer)
					{

						final FragmentSegmentAssignmentState assignment = assignmentOptional.get();

						final long lastSelection = selectedIds.get().getLastSelection();

						if (lastSelection == Label.INVALID) { return; }

						final AffineTransform3D viewerTransform = new AffineTransform3D();
						final ViewerState       viewerState     = viewer.getState();
						viewerState.getViewerTransform(viewerTransform);
						final int               level  = viewerState.getBestMipMapLevel(
								viewerTransform,
								getIndexOf(source, viewerState)
						                                                               );
						final AffineTransform3D affine = new AffineTransform3D();
						dataSource.getSourceTransform(0, level, affine);
						final RealTransformRealRandomAccessible<I, InverseRealTransform> transformedSource = RealViews
								.transformReal(
								dataSource.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR),
								affine
						                                                                                                            );
						final RealRandomAccess<I>                                        access            =
								transformedSource.realRandomAccess();
						viewer.getMouseCoordinates(access);
						access.setPosition(0l, 2);
						viewer.displayToGlobalCoordinates(access);
						final I    val = access.get();
						final long id  = val.getIntegerLong();

						final Optional<Detach> detach = assignment.getDetachAction(id, lastSelection);
						detach.ifPresent(assignment::apply);

					}
				}
			}
		}

	}

	private class ConfirmSelection
	{
		public <I extends IntegerType<I>> void click(final MouseEvent e)
		{
			LOG.debug("Clicked confirm selection!");
			final Optional<Source<?>> optionalSource = getCurrentSource();
			if (!optionalSource.isPresent())
			{
				LOG.debug("No source present!");
				return;
			}
			final Source<?> source = optionalSource.get();
			if (source instanceof DataSource<?, ?> && sourceInfo.getState(source) instanceof LabelSourceState<?, ?>)
			{
				@SuppressWarnings("unchecked") final DataSource<I, ?> dataSource = (DataSource<I, ?>) source;
				final SourceState<?, ?> currentSourceState = sourceInfo.getState(source);
				if (!(currentSourceState instanceof LabelSourceState<?, ?>))
				{
					LOG.warn("Not a label source -- cannot select  id.");
					return;
				}
				@SuppressWarnings("unchecked") final LabelSourceState<I, ?> state = (LabelSourceState<I, ?>)
						currentSourceState;
				final Optional<SelectedIds>                    selectedIds        = Optional.ofNullable(state
						.selectedIds());
				final Optional<FragmentSegmentAssignmentState> assignmentOptional = Optional.ofNullable(state
						.assignment());
				if (selectedIds.isPresent() && assignmentOptional.isPresent())
				{
					synchronized (viewer)
					{
						final FragmentSegmentAssignmentState assignment = assignmentOptional.get();

						final long[] activeFragments = selectedIds.get().getActiveIds();
						final long[] activeSegments  = Arrays.stream(activeFragments).map(id -> assignment.getSegment
								(id)).toArray();

						if (activeSegments.length > 1)
						{
							LOG.warn("More than one segment active, not doing anything!");
							return;
						}

						if (activeSegments.length == 0)
						{
							LOG.warn("No segments active, not doing anything!");
							return;
						}

						final AffineTransform3D viewerTransform = new AffineTransform3D();
						final ViewerState       viewerState     = viewer.getState();
						viewerState.getViewerTransform(viewerTransform);
						final int               level  = viewerState.getBestMipMapLevel(
								viewerTransform,
								getIndexOf(source, viewerState)
						                                                               );
						final AffineTransform3D affine = new AffineTransform3D();
						dataSource.getSourceTransform(0, level, affine);
						final RealTransformRealRandomAccessible<I, InverseRealTransform> transformedSource = RealViews
								.transformReal(
								dataSource.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR),
								affine
						                                                                                                            );
						final RealRandomAccess<I>                                        access            =
								transformedSource.realRandomAccess();
						viewer.getMouseCoordinates(access);
						access.setPosition(0l, 2);
						viewer.displayToGlobalCoordinates(access);
						final I            val                 = access.get();
						final long         selectedFragment    = val.getIntegerLong();
						final long         selectedSegment     = assignment.getSegment(selectedFragment);
						final TLongHashSet selectedSegmentsSet = new TLongHashSet(new long[] {selectedSegment});
						final TLongHashSet visibleFragmentsSet = new TLongHashSet();

						if (activeSegments.length == 0 || activeSegments[0] == selectedSegment)
						{
							LOG.debug("confirm merge and separate of single segment");
							visitEveryDisplayPixel(
									dataSource,
									viewer,
									obj -> visibleFragmentsSet.add(obj.getIntegerLong())
							                      );
							final long[]                     visibleFragments            = visibleFragmentsSet
									.toArray();
							final long[]                     fragmentsInActiveSegment    = Arrays.stream(
									visibleFragments).filter(frag -> selectedSegmentsSet.contains(assignment
									.getSegment(
									frag))).toArray();
							final long[]                     fragmentsNotInActiveSegment = Arrays.stream(
									visibleFragments).filter(frag -> !selectedSegmentsSet.contains(assignment
									.getSegment(
									frag))).toArray();
							final Optional<AssignmentAction> action                      = assignment
									.getConfirmGroupingAction(
									fragmentsInActiveSegment,
									fragmentsNotInActiveSegment
							                                                                                                  );
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
									new TLongHashSet()
							                                                                     ));
							visitEveryDisplayPixel(dataSource, viewer, obj -> {
								final long         frag  = obj.getIntegerLong();
								final TLongHashSet frags = fragmentsBySegment.get(assignment.getSegment(frag));
								if (frags != null)
								{
									frags.add(frag);
								}
							});
							final Optional<AssignmentAction> action = assignment.getConfirmTwoSegmentsAction(
									fragmentsBySegment.get(relevantSegments[0]).toArray(),
									fragmentsBySegment.get(relevantSegments[1]).toArray()
							                                                                                );
							action.ifPresent(assignment::apply);
						}

					}
				}
			}
		}
	}

	private Optional<Source<?>> getCurrentSource()
	{
		return Optional.ofNullable(sourceInfo.currentSourceProperty().get());
	}

	private static int getIndexOf(final Source<?> source, final ViewerState state)
	{
		return state
				.getSources()
				.stream()
				.map(src -> src.getSpimSource())
				.collect(Collectors.toList())
				.indexOf(source);
	}

	private static <I> void visitEveryDisplayPixel(
			final DataSource<I, ?> dataSource,
			final ViewerPanelFX viewer,
			final Consumer<I> doAtPixel)
	{
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		final ViewerState       state           = viewer.getState();
		state.getViewerTransform(viewerTransform);
		final int level = state.getBestMipMapLevel(viewerTransform, getIndexOf(dataSource, state));
		dataSource.getSourceTransform(0, level, sourceTransform);

		final RealRandomAccessible<I>                                    interpolatedSource = dataSource.getInterpolatedDataSource(
				0,
				level,
				Interpolation.NEARESTNEIGHBOR
		                                                                                                                          );
		final RealTransformRealRandomAccessible<I, InverseRealTransform> transformedSource  = RealViews.transformReal(
				interpolatedSource,
				sourceTransform
		                                                                                                             );

		final int w = (int) viewer.getWidth();
		final int h = (int) viewer.getHeight();
		final IntervalView<I> screenLabels =
				Views.interval(
						Views.hyperSlice(
								RealViews.affine(transformedSource, viewerTransform), 2, 0),
						new FinalInterval(w, h)
				              );

		visitEveryPixel(screenLabels, doAtPixel);
	}

	private static <I> void visitEveryPixel(
			final RandomAccessibleInterval<I> img,
			final Consumer<I> doAtPixel)
	{
		final Cursor<I> cursor = Views.flatIterable(img).cursor();

		while (cursor.hasNext())
		{
			doAtPixel.accept(cursor.next());
		}
	}

}
