package org.janelia.saalfeldlab.paintera.state;

import bdv.viewer.Interpolation;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.label.Label;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys;
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import static org.janelia.saalfeldlab.fx.actions.PainteraActionSetKt.painteraActionSet;

public class LabelSourceStateMergeDetachHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final LongPredicate FOREGROUND_CHECK = Label::isForeground;

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

	public List<ActionSet> makeActionSets(Supplier<ViewerPanelFX> activeViewerSupplier) {

		final var mergeFragments = painteraActionSet("MergeFragments", LabelActionType.Merge, actionSet -> {
			actionSet.addMouseAction(MouseEvent.MOUSE_CLICKED, action -> {
				action.keysDown(KeyCode.SHIFT);
				action.verify(event -> activeViewerSupplier.get() != null);
				action.verifyButtonTrigger(MouseButton.PRIMARY);
				action.onAction(mouseEvent -> new MergeFragments(activeViewerSupplier.get()).accept(mouseEvent));
			});
			actionSet.addKeyAction(KeyEvent.KEY_PRESSED, action -> {
				action.keyMatchesBinding(LabelSourceStateKeys.MERGE_ALL_SELECTED);
				action.onAction(event -> mergeAllSelected());
			});
		});
		final var detachFragments = painteraActionSet("DetachFragment", LabelActionType.Split, actionSet -> {
			actionSet.addMouseAction(MouseEvent.MOUSE_CLICKED, action -> {
				action.setName("DetachFragment");
				action.keysDown(KeyCode.SHIFT);
				action.verifyButtonTrigger(MouseButton.SECONDARY);
				action.verify(event -> activeViewerSupplier.get() != null);
				action.onAction(mouseEvent -> new DetachFragment(activeViewerSupplier.get()).accept(mouseEvent));
			});
		});

		return List.of(mergeFragments, detachFragments);
	}

	private void mergeAllSelected() {

		FragmentSegmentAssignment.mergeAllSelected(assignment, selectedIds, idService);
	}

	private class MergeFragments implements Consumer<MouseEvent> {

		private final ViewerPanelFX viewer;

		private MergeFragments(final ViewerPanelFX viewer) {

			this.viewer = viewer;
		}

		@Override
		public void accept(final MouseEvent e) {

			synchronized (viewer) {

				final long lastSelection = selectedIds.getLastSelection();

				if (lastSelection == Label.INVALID) {
					return;
				}

				final AffineTransform3D screenScaleTransform = new AffineTransform3D();
				viewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
				final int level = viewer.getState().getBestMipMapLevel(screenScaleTransform, source);

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

				if (FOREGROUND_CHECK.test(id)) {
					LOG.debug("Merging fragments: {} -- last selection: {}", id, lastSelection);
					final Optional<Merge> action = assignment.getMergeAction(
							id,
							lastSelection,
							idService::next);
					action.ifPresent(assignment::apply);
				}
			}
		}

	}

	private class DetachFragment implements Consumer<MouseEvent> {

		private final ViewerPanelFX viewer;

		private DetachFragment(final ViewerPanelFX viewer) {

			this.viewer = viewer;
		}

		@Override
		public void accept(final MouseEvent e) {

			final long lastSelection = selectedIds.getLastSelection();

			if (lastSelection == Label.INVALID) {
				return;
			}

			final AffineTransform3D screenScaleTransform = new AffineTransform3D();
			viewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
			final int level = viewer.getState().getBestMipMapLevel(screenScaleTransform, source);

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
			final long id = val.getIntegerLong();

			if (FOREGROUND_CHECK.test(id)) {
				final Optional<Detach> detach = assignment.getDetachAction(id, lastSelection);
				detach.ifPresent(action -> {
							final long previousSegment = assignment.getSegment(lastSelection);
							if (id == lastSelection && previousSegment != id && previousSegment != Label.INVALID) {
								/* Special case where we detach the current active fragment from its own segment.
								 * In that case, we want the previous segment to still be active.*/
								selectedIds.activateAlso(previousSegment);
							}
							assignment.apply(action);

						}
				);
			}
		}

	}

}
