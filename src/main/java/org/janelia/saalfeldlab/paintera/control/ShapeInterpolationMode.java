package org.janelia.saalfeldlab.paintera.control;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.event.MouseClickFX;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions;
import org.janelia.saalfeldlab.paintera.control.actions.LabelAction;
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction;
import org.janelia.saalfeldlab.paintera.control.actions.NavigationAction;
import org.janelia.saalfeldlab.paintera.control.actions.PaintAction;
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.PredicateDataSource.PredicateConverter;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.util.Affine3DHelpers;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.converter.Converters;
import net.imglib2.converter.logical.Logical;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.Label;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class ShapeInterpolationMode<D extends IntegerType<D>>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static enum ModeState
	{
		Selecting,
		Interpolating,
		Review
	}

	private static final class SelectedObjectInfo
	{
		final RealPoint sourceClickPosition;
		final Interval sourceBoundingBox;

		SelectedObjectInfo(final RealPoint sourceClickPosition, final Interval sourceBoundingBox)
		{
			this.sourceClickPosition = sourceClickPosition;
			this.sourceBoundingBox = sourceBoundingBox;
		}
	}

	// TODO: instead of keeping track of the bounding box in the source coordinate space, track affected interval in the current viewer plane.
	// This would make the union bounding box smaller. Currently it may be larger than necessary because of the rotating transform from source to display coordinates.
	private static final class SectionInfo
	{
		final Mask<UnsignedLongType> mask;
		final AffineTransform3D sourceToDisplayTransform;
		final Interval sourceBoundingBox;

		SectionInfo(final Mask<UnsignedLongType> mask, final AffineTransform3D sourceToDisplayTransform, final Interval sourceBoundingBox)
		{
			this.mask = mask;
			this.sourceToDisplayTransform = sourceToDisplayTransform;
			this.sourceBoundingBox = sourceBoundingBox;
		}
	}

	private static final double FILL_DEPTH = 1.0;

	private static final int MASK_SCALE_LEVEL = 0;

	private static final int SHAPE_INTERPOLATION_SCALE_LEVEL = MASK_SCALE_LEVEL;

	private static final Color MASK_COLOR = Color.web("00CCFF");

	private static final Predicate<UnsignedLongType> FOREGROUND_CHECK = t -> t.get() > 0;

	private final ObjectProperty<ViewerPanelFX> activeViewer = new SimpleObjectProperty<>();

	private final MaskedSource<D, ?> source;
	private final SelectedIds selectedIds;
	private final IdService idService;
	private final HighlightingStreamConverter<?> converter;

	private final AllowedActions allowedActions;
	private final AllowedActions allowedActionsWhenSelected;

	private AllowedActions lastAllowedActions;
	private long lastSelectedId;
	private long[] lastActiveIds;

	private final TLongObjectMap<SelectedObjectInfo> selectedObjects = new TLongObjectHashMap<>();
	private long currentFillValue;

	private SectionInfo sectionInfo1, sectionInfo2;
	private Mask<UnsignedLongType> mask;

	private ObjectProperty<ModeState> modeState = new SimpleObjectProperty<>();
	private long newLabelId;

	private Thread workerThread;

	public ShapeInterpolationMode(
			final MaskedSource<D, ?> source,
			final SelectedIds selectedIds,
			final IdService idService,
			final HighlightingStreamConverter<?> converter)
	{
		this.source = source;
		this.selectedIds = selectedIds;
		this.idService = idService;
		this.converter = converter;

		final Consumer<PainteraBaseView> cleanup = baseView -> exitMode(baseView, false);
		this.allowedActions = new AllowedActions(
				NavigationAction.of(NavigationAction.Drag, NavigationAction.Zoom, NavigationAction.Scroll),
				LabelAction.none(),
				PaintAction.none(),
				MenuAction.of(MenuAction.ToggleMaximizeViewer),
				cleanup
			);
		this.allowedActionsWhenSelected = new AllowedActions(
				NavigationAction.of(NavigationAction.Drag, NavigationAction.Zoom),
				LabelAction.none(),
				PaintAction.none(),
				MenuAction.of(MenuAction.ToggleMaximizeViewer),
				cleanup
			);
	}

	public ObjectProperty<ViewerPanelFX> activeViewerProperty()
	{
		return activeViewer;
	}

	public ObjectProperty<ModeState> modeStateProperty()
	{
		return modeState;
	}

	public EventHandler<Event> modeHandler(final PainteraBaseView paintera, final KeyTracker keyTracker)
	{
		final DelegateEventHandlers.AnyHandler filter = DelegateEventHandlers.handleAny();
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"enter shape interpolation mode",
						e -> {e.consume(); enterMode(paintera, (ViewerPanelFX) e.getTarget());},
						e -> e.getTarget() instanceof ViewerPanelFX &&
							!isModeOn() &&
							!source.isApplyingMaskProperty().get() &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.S)
					)
			);
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"fix selection",
						e -> {e.consume(); fixSelection(paintera);},
						e -> modeState.get() == ModeState.Selecting &&
							!selectedObjects.isEmpty() &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.S)
					)
			);
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"apply mask",
						e -> {e.consume(); applyMask(paintera);},
						e -> modeState.get() == ModeState.Review &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.S)
					)
			);
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"exit shape interpolation mode",
						e -> {e.consume(); exitMode(paintera, false);},
						e -> isModeOn() && keyTracker.areOnlyTheseKeysDown(KeyCode.ESCAPE)
					)
			);
		filter.addEventHandler(MouseEvent.ANY, new MouseClickFX(
				"select object in current section",
				e -> {e.consume(); selectObject(paintera, e.getX(), e.getY(), true);},
				e -> modeState.get() == ModeState.Selecting && e.isPrimaryButtonDown() && keyTracker.noKeysActive())
			.handler());
		filter.addEventHandler(MouseEvent.ANY, new MouseClickFX(
				"toggle object in current section",
				e -> {e.consume(); selectObject(paintera, e.getX(), e.getY(), false);},
				e -> modeState.get() == ModeState.Selecting &&
					((e.isSecondaryButtonDown() && keyTracker.noKeysActive()) ||
					(e.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL))))
			.handler());
		return filter;
	}

	public void enterMode(final PainteraBaseView paintera, final ViewerPanelFX viewer)
	{
		if (isModeOn())
		{
			LOG.info("Already in shape interpolation mode");
			return;
		}
		LOG.info("Entering shape interpolation mode");
		activeViewer.set(viewer);
		setDisableOtherViewers(paintera, true);

		lastAllowedActions = paintera.allowedActionsProperty().get();
		paintera.allowedActionsProperty().set(allowedActions);

		lastSelectedId = selectedIds.getLastSelection();
		lastActiveIds = selectedIds.getActiveIds();
		newLabelId = idService.next();
		converter.setColor(newLabelId, MASK_COLOR);
		selectedIds.activate(newLabelId);

		modeState.set(ModeState.Selecting);
	}

	public void exitMode(final PainteraBaseView paintera, final boolean completed)
	{
		if (!isModeOn())
		{
			LOG.info("Not in shape interpolation mode");
			return;
		}
		LOG.info("Exiting shape interpolation mode");
		setDisableOtherViewers(paintera, false);

		if (!completed) // extra cleanup if the mode is aborted
		{
			if (workerThread != null)
			{
				workerThread.interrupt();
				try {
					workerThread.join();
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}
			}

			selectedIds.activate(lastActiveIds);
			selectedIds.activateAlso(lastSelectedId);

			source.resetMasks();
		}

		converter.removeColor(newLabelId);
		newLabelId = Label.INVALID;

		paintera.allowedActionsProperty().set(lastAllowedActions);
		lastAllowedActions = null;

		currentFillValue = 0;
		selectedObjects.clear();
		sectionInfo1 = sectionInfo2 = null;
		mask = null;
		modeState.set(null);

		workerThread = null;
		lastSelectedId = Label.INVALID;
		lastActiveIds = null;

		activeViewer.get().requestRepaint();
		activeViewer.set(null);
	}

	public boolean isModeOn()
	{
		return modeState.get() != null;
	}

	private void createMask() throws MaskInUse
	{
		final int time = activeViewer.get().getState().timepointProperty().get();
		final int level = MASK_SCALE_LEVEL;
		final MaskInfo<UnsignedLongType> maskInfo = new MaskInfo<>(time, level, new UnsignedLongType(newLabelId));
		mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
	}

	private void setDisableOtherViewers(final PainteraBaseView paintera, final boolean disable)
	{
		for (final ViewerPanelFX viewer : getViewerPanels(paintera))
		{
			if (viewer != activeViewer.get())
			{
				viewer.setDisable(disable);
				if (disable)
				{
					final ColorAdjust grayedOutEffect = new ColorAdjust();
					grayedOutEffect.setContrast(-0.2);
					grayedOutEffect.setBrightness(-0.5);
					viewer.setEffect(grayedOutEffect);
				}
				else
				{
					viewer.setEffect(null);
				}
			}
		}
	}

	private void fixSelection(final PainteraBaseView paintera)
	{
		if (sectionInfo1 == null)
		{
			LOG.debug("Fix selection in the first section");
			sectionInfo1 = createSectionInfo();
			selectedObjects.clear();
			source.resetMasks();
			mask = null;
			activeViewerProperty().get().requestRepaint();
			paintera.allowedActionsProperty().set(allowedActions);
		}
		else
		{
			LOG.debug("Fix selection in the second section");
			sectionInfo2 = createSectionInfo();
			interpolateBetweenSections(paintera);
		}
	}

	private void applyMask(final PainteraBaseView paintera)
	{
		final Interval sectionsUnionSourceInterval = Intervals.union(
				sectionInfo1.sourceBoundingBox,
				sectionInfo2.sourceBoundingBox
			);
		LOG.info("Applying interpolated mask using bounding box of size {}", Intervals.dimensionsAsLongArray(sectionsUnionSourceInterval));
		source.applyMask(source.getCurrentMask(), sectionsUnionSourceInterval, FOREGROUND_CHECK);
		exitMode(paintera, true);
	}

	private SectionInfo createSectionInfo()
	{
		Interval selectionSourceBoundingBox = null;
		for (final TLongObjectIterator<SelectedObjectInfo> it = selectedObjects.iterator(); it.hasNext();)
		{
			it.advance();
			if (selectionSourceBoundingBox == null)
				selectionSourceBoundingBox = it.value().sourceBoundingBox;
			else
				selectionSourceBoundingBox = Intervals.union(selectionSourceBoundingBox, it.value().sourceBoundingBox);
		}
		return new SectionInfo(
				mask,
				getMaskDisplayTransformIgnoreScaling(SHAPE_INTERPOLATION_SCALE_LEVEL),
				selectionSourceBoundingBox
			);
	}

	@SuppressWarnings("unchecked")
	private void interpolateBetweenSections(final PainteraBaseView paintera)
	{
		modeState.set(ModeState.Interpolating);

		workerThread = new Thread(() ->
		{
			final SectionInfo[] sectionInfoPair = {sectionInfo1, sectionInfo2};

			final Interval affectedUnionSourceInterval = Intervals.union(
					sectionInfoPair[0].sourceBoundingBox,
					sectionInfoPair[1].sourceBoundingBox
				);

			final Interval[] displaySectionIntervalPair = new Interval[2];
			final RandomAccessibleInterval<UnsignedLongType>[] sectionPair = new RandomAccessibleInterval[2];
			for (int i = 0; i < 2; ++i)
			{
				final SectionInfo newSectionInfo = new SectionInfo(sectionInfoPair[i].mask, sectionInfoPair[i].sourceToDisplayTransform, affectedUnionSourceInterval);
				final RandomAccessibleInterval<UnsignedLongType> section = getTransformedMaskSection(newSectionInfo);
				displaySectionIntervalPair[i] = new FinalInterval(section);
				sectionPair[i] = new ArrayImgFactory<>(new UnsignedLongType()).create(section);
				final Cursor<UnsignedLongType> srcCursor = Views.flatIterable(section).cursor();
				final Cursor<UnsignedLongType> dstCursor = Views.flatIterable(sectionPair[i]).cursor();
				while (dstCursor.hasNext() || srcCursor.hasNext())
					dstCursor.next().set(srcCursor.next());
			}

			// compute distance transform on both sections
			final RandomAccessibleInterval<FloatType>[] distanceTransformPair = new RandomAccessibleInterval[2];
			for (int i = 0; i < 2; ++i)
			{
				if (Thread.currentThread().isInterrupted())
					return;
				distanceTransformPair[i] = new ArrayImgFactory<>(new FloatType()).create(sectionPair[i]);
				final RandomAccessibleInterval<BoolType> binarySection = Converters.convert(sectionPair[i], new PredicateConverter<>(FOREGROUND_CHECK), new BoolType());
				computeSignedDistanceTransform(binarySection, distanceTransformPair[i], DISTANCE_TYPE.EUCLIDIAN);
			}

			final double distanceBetweenSections = computeDistanceBetweenSections(sectionInfo1, sectionInfo2);
			final AffineTransform3D transformToSource = new AffineTransform3D();
			transformToSource
				.preConcatenate(new Translation3D(displaySectionIntervalPair[0].min(0), displaySectionIntervalPair[0].min(1), 0))
				.preConcatenate(sectionInfo1.sourceToDisplayTransform.inverse());

			final RealRandomAccessible<UnsignedLongType> interpolatedShapeMask = getInterpolatedDistanceTransformMask(
					distanceTransformPair[0],
					distanceTransformPair[1],
					distanceBetweenSections,
					new UnsignedLongType(1),
					transformToSource
				);

			final RealRandomAccessible<VolatileUnsignedLongType> volatileInterpolatedShapeMask = getInterpolatedDistanceTransformMask(
					distanceTransformPair[0],
					distanceTransformPair[1],
					distanceBetweenSections,
					new VolatileUnsignedLongType(1),
					transformToSource
				);

			if (Thread.currentThread().isInterrupted())
				return;

			try
			{
				synchronized (source)
				{
					source.resetMasks();
					source.setMask(
							mask.info,
							interpolatedShapeMask,
							volatileInterpolatedShapeMask,
							FOREGROUND_CHECK
						);
				}

				for (final ViewerPanelFX viewer : getViewerPanels(paintera))
					viewer.requestRepaint();
			}
			catch (final MaskInUse e)
			{
				LOG.error("Label source already has an active mask");
			}

			InvokeOnJavaFXApplicationThread.invoke(() -> {
				modeState.set(ModeState.Review);
				paintera.allowedActionsProperty().set(allowedActions);
			});
		});
		workerThread.start();
	}

	private static <R extends RealType<R> & NativeType<R>, B extends BooleanType<B>> void computeSignedDistanceTransform(
			final RandomAccessibleInterval<B> mask,
			final RandomAccessibleInterval<R> target,
			final DISTANCE_TYPE distanceType,
			final double... weights)
	{
		final RandomAccessibleInterval<R> distanceOutside = target;
		final RandomAccessibleInterval<R> distanceInside = new ArrayImgFactory<>(Util.getTypeFromInterval(target)).create(target);
		DistanceTransform.binaryTransform(mask, distanceOutside, distanceType, weights);
		DistanceTransform.binaryTransform(Logical.complement(mask), distanceInside, distanceType, weights);
		LoopBuilder.setImages(distanceOutside, distanceInside, target).forEachPixel((outside, inside, result) -> {
			switch (distanceType)
			{
			case EUCLIDIAN:
				result.setReal(Math.sqrt(outside.getRealDouble()) - Math.sqrt(inside.getRealDouble()));
				break;
			case L1:
				result.setReal(outside.getRealDouble() - inside.getRealDouble());
				break;
			}
		});
	}

	private static <R extends RealType<R>, T extends NativeType<T> & RealType<T>> RealRandomAccessible<T> getInterpolatedDistanceTransformMask(
			final RandomAccessibleInterval<R> dt1,
			final RandomAccessibleInterval<R> dt2,
			final double distance,
			final T targetValue,
			final AffineTransform3D transformToSource)
	{
		final RandomAccessibleInterval<R> distanceTransformStack = Views.stack(dt1, dt2);

		final R extendValue = Util.getTypeFromInterval(distanceTransformStack).createVariable();
		extendValue.setReal(extendValue.getMaxValue());
		final RealRandomAccessible<R> interpolatedDistanceTransform = Views.interpolate(
				Views.extendValue(distanceTransformStack, extendValue),
				new NLinearInterpolatorFactory<>()
			);

		final double distanceSign = Math.signum(distance);
		final double padding = 0.5; // slightly stretches the mask past the end sections to ensure that it's visible in the current plane
		final RealRandomAccessible<R> scaledInterpolatedDistanceTransform = RealViews.affineReal(
				interpolatedDistanceTransform,
				new AffineTransform3D()
					.preConcatenate(new Scale3D(1, 1, -(distance + padding * distanceSign)))
					.preConcatenate(new Translation3D(0, 0, padding * 0.5 * distanceSign))
			);

		final T emptyValue = targetValue.createVariable();
		final RealRandomAccessible<T> interpolatedShape = Converters.convert(
				scaledInterpolatedDistanceTransform,
				(in, out) -> out.set(in.getRealDouble() <= 0 ? targetValue : emptyValue),
				emptyValue.createVariable()
			);

		return RealViews.affineReal(interpolatedShape, transformToSource);
	}

	private RandomAccessibleInterval<UnsignedLongType> getTransformedMaskSection(final SectionInfo sectionInfo)
	{
		final RealInterval sectionBounds = sectionInfo.sourceToDisplayTransform.estimateBounds(sectionInfo.sourceBoundingBox);
		final Interval sectionInterval = Intervals.smallestContainingInterval(sectionBounds);
		final RealRandomAccessible<UnsignedLongType> transformedMask = getTransformedMask(sectionInfo.mask, sectionInfo.sourceToDisplayTransform);
		final RandomAccessibleInterval<UnsignedLongType> transformedMaskInterval = Views.interval(Views.raster(transformedMask), sectionInterval);
		return Views.hyperSlice(transformedMaskInterval, 2, 0l);
	}

	private static RealRandomAccessible<UnsignedLongType> getTransformedMask(final Mask<UnsignedLongType> mask, final AffineTransform3D transform)
	{
		final RealRandomAccessible<UnsignedLongType> interpolatedMask = Views.interpolate(
				Views.extendValue(mask.mask, new UnsignedLongType(Label.OUTSIDE)),
				new NearestNeighborInterpolatorFactory<>()
			);
		return RealViews.affine(interpolatedMask, transform);
	}

	private static double computeDistanceBetweenSections(final SectionInfo s1, final SectionInfo s2)
	{
		final double[] pos1 = new double[3], pos2 = new double[3];
		s1.sourceToDisplayTransform.apply(pos1, pos1);
		s2.sourceToDisplayTransform.apply(pos2, pos2);
		return pos2[2] - pos1[2]; // We care only about the shift between the sections (Z distance in the viewer)
	}

	private void selectObject(final PainteraBaseView paintera, final double x, final double y, final boolean deactivateOthers)
	{
		// create the mask if needed
		if (mask == null)
		{
			try {
				createMask();
			} catch (final MaskInUse e) {
				e.printStackTrace();
			}
		}

		final UnsignedLongType maskValue = getMaskValue(x, y);
		if (maskValue.get() == Label.OUTSIDE)
			return;

		final boolean wasSelected = FOREGROUND_CHECK.test(maskValue);
		final int numSelectedObjects = selectedObjects.size();
		converter.setColor(mask.info.value.get(), MASK_COLOR);

		LOG.debug("Object was clicked: deactivateOthers={}, wasSelected={}, numSelectedObjects", deactivateOthers, wasSelected, numSelectedObjects);

		if (deactivateOthers)
		{
			for (final TLongObjectIterator<SelectedObjectInfo> it = selectedObjects.iterator(); it.hasNext();)
			{
				it.advance();
				final double[] deselectDisplayPos = getDisplayCoordinates(it.value().sourceClickPosition);
				runFloodFillToDeselect(deselectDisplayPos[0], deselectDisplayPos[1]);
			}
			selectedObjects.clear();
		}

		if (!wasSelected || (deactivateOthers && numSelectedObjects > 1))
		{
			final Pair<Long, Interval> fillValueAndInterval = runFloodFillToSelect(x, y);
			selectedObjects.put(fillValueAndInterval.getA(), new SelectedObjectInfo(getSourceCoordinates(x, y), fillValueAndInterval.getB()));
		}
		else
		{
			final long oldFillValue = runFloodFillToDeselect(x, y);
			selectedObjects.remove(oldFillValue);
		}

		// free the mask if there are no selected objects
		if (selectedObjects.isEmpty())
		{
			source.resetMasks();
			mask = null;
		}

		activeViewer.get().requestRepaint();
		paintera.allowedActionsProperty().set(selectedObjects.isEmpty() ? allowedActions : allowedActionsWhenSelected);
	}

	/**
	 * Flood-fills the mask using a new fill value to mark the object as selected.
	 *
	 * @param x
	 * @param y
	 * @return the fill value of the selected object and the affected interval in source coordinates
	 */
	private Pair<Long, Interval> runFloodFillToSelect(final double x, final double y)
	{
		final Interval affectedInterval = FloodFill2D.fillMaskAt(x, y, activeViewer.get(), mask, source, ++currentFillValue, FILL_DEPTH);
		return new ValuePair<>(currentFillValue, affectedInterval);
	}

	/**
	 * Flood-fills the mask using the background value to remove the object from the selection.
	 *
	 * @param x
	 * @param y
	 * @return the fill value of the deselected object
	 */
	private long runFloodFillToDeselect(final double x, final double y)
	{
		// set the predicate to accept only the fill value at the clicked location to avoid deselecting adjacent objects.
		final long maskValue = getMaskValue(x, y).get();
		final RandomAccessibleInterval<BoolType> predicate = Converters.convert(
				mask.mask,
				(in, out) -> out.set(in.getIntegerLong() == maskValue),
				new BoolType()
			);
		FloodFill2D.fillMaskAt(x, y, activeViewer.get(), mask, predicate, getMaskTransform(), Label.BACKGROUND, FILL_DEPTH);
		return maskValue;
	}

	private UnsignedLongType getMaskValue(final double x, final double y)
	{
		final RealPoint sourcePos = getSourceCoordinates(x, y);
		final RandomAccess<UnsignedLongType> maskAccess = Views.extendValue(mask.mask, new UnsignedLongType(Label.OUTSIDE)).randomAccess();
		for (int d = 0; d < sourcePos.numDimensions(); ++d)
			maskAccess.setPosition(Math.round(sourcePos.getDoublePosition(d)), d);
		return maskAccess.get();
	}

	private AffineTransform3D getMaskTransform()
	{
		final AffineTransform3D maskTransform = new AffineTransform3D();
		final int time = activeViewer.get().getState().timepointProperty().get();
		final int level = MASK_SCALE_LEVEL;
		source.getSourceTransform(time, level, maskTransform);
		return maskTransform;
	}

	private AffineTransform3D getDisplayTransform()
	{
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		activeViewer.get().getState().getViewerTransform(viewerTransform);
		return viewerTransform;
	}

	private AffineTransform3D getMaskDisplayTransform()
	{
		return getDisplayTransform().concatenate(getMaskTransform());
	}

	/**
	 * Returns the transformation to bring the mask to the current viewer plane at the requested mipmap level.
	 * Ignores the scaling in the viewer and in the mask and instead uses the requested mipmap level for scaling.
	 *
	 * @param level
	 * @return
	 */
	private AffineTransform3D getMaskDisplayTransformIgnoreScaling(final int targetLevel)
	{
		final AffineTransform3D maskMipmapDisplayTransform = getMaskDisplayTransformIgnoreScaling();
		final int maskLevel = MASK_SCALE_LEVEL;
		if (targetLevel != maskLevel)
		{
			// scale with respect to the given mipmap level
			final int time = activeViewer.get().getState().timepointProperty().get();
			final Scale3D relativeScaleTransform = new Scale3D(DataSource.getRelativeScales(source, time, maskLevel, targetLevel));
			maskMipmapDisplayTransform.preConcatenate(relativeScaleTransform.inverse());
		}
		return maskMipmapDisplayTransform;
	}

	/**
	 * Returns the transformation to bring the mask to the current viewer plane.
	 * Ignores the scaling in the viewer and in the mask.
	 *
	 * @return
	 */
	private AffineTransform3D getMaskDisplayTransformIgnoreScaling()
	{
		final AffineTransform3D viewerTransform = getDisplayTransform();
		// undo scaling in the viewer
		final double[] viewerScale = new double[viewerTransform.numDimensions()];
		Arrays.setAll(viewerScale, d -> Affine3DHelpers.extractScale(viewerTransform, d));
		final Scale3D scalingTransform = new Scale3D(viewerScale);
		// neutralize mask scaling if there is any
		final int time = activeViewer.get().getState().timepointProperty().get();
		final int level = MASK_SCALE_LEVEL;
		scalingTransform.concatenate(new Scale3D(DataSource.getScale(source, time, level)));
		// build the resulting transform
		return viewerTransform.preConcatenate(scalingTransform.inverse()).concatenate(getMaskTransform());
	}

	private RealPoint getSourceCoordinates(final double x, final double y)
	{
		final AffineTransform3D maskTransform = getMaskTransform();
		final RealPoint sourcePos = new RealPoint(maskTransform.numDimensions());
		activeViewer.get().displayToSourceCoordinates(x, y, maskTransform, sourcePos);
		return sourcePos;
	}

	private double[] getDisplayCoordinates(final RealPoint sourcePos)
	{
		final AffineTransform3D maskDisplayTransform = getMaskDisplayTransform();
		final RealPoint displayPos = new RealPoint(maskDisplayTransform.numDimensions());
		maskDisplayTransform.apply(sourcePos, displayPos);
		assert Util.isApproxEqual(displayPos.getDoublePosition(2), 0.0, 1e-10);
		return new double[] {displayPos.getDoublePosition(0), displayPos.getDoublePosition(1)};
	}

	private static ViewerPanelFX[] getViewerPanels(final PainteraBaseView paintera)
	{
		return new ViewerPanelFX[] {
				paintera.orthogonalViews().topLeft().viewer(),
				paintera.orthogonalViews().topRight().viewer(),
				paintera.orthogonalViews().bottomLeft().viewer()
			};
	}
}
