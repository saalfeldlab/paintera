package org.janelia.saalfeldlab.paintera.control;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.event.MouseClickFX;
import org.janelia.saalfeldlab.labels.InterpolateBetweenSections;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions;
import org.janelia.saalfeldlab.paintera.control.actions.LabelAction;
import org.janelia.saalfeldlab.paintera.control.actions.NavigationAction;
import org.janelia.saalfeldlab.paintera.control.actions.PaintAction;
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class ShapeInterpolationMode<D extends IntegerType<D>>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static enum ModeState
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

	private static final class SectionInfo
	{
		final AffineTransform3D sourceToDisplayTransform;
		final Interval sourceBoundingBox;

		SectionInfo(final AffineTransform3D sourceToDisplayTransform, final Interval sourceBoundingBox)
		{
			this.sourceToDisplayTransform = sourceToDisplayTransform;
			this.sourceBoundingBox = sourceBoundingBox;
		}
	}

	private static final AllowedActions allowedActions;
	private static final AllowedActions allowedActionsWhenSelected;
	static
	{
		allowedActions = new AllowedActions(
			NavigationAction.of(NavigationAction.Drag, NavigationAction.Zoom, NavigationAction.Scroll),
			LabelAction.none(),
			PaintAction.none()
		);
		allowedActionsWhenSelected = new AllowedActions(
				NavigationAction.of(NavigationAction.Drag, NavigationAction.Zoom),
				LabelAction.none(),
				PaintAction.none()
			);
	}

	private static final double FILL_DEPTH = 2.0;

	private static final int MASK_SCALE_LEVEL = 0;

	private static final int SHAPE_INTERPOLATION_SCALE_LEVEL = MASK_SCALE_LEVEL;

	private static final Color MASK_COLOR = Color.web("00CCFF");

	private static final Predicate<UnsignedLongType> FOREGROUND_CHECK = t -> t.get() > 0;

	private static final long INTERPOLATION_FILL_VALUE = 1;

	private final ObjectProperty<ViewerPanelFX> activeViewer = new SimpleObjectProperty<>();

	private final MaskedSource<D, ?> source;
	private final SelectedIds selectedIds;
	private final IdService idService;
	private final HighlightingStreamConverter<?> converter;

	private AllowedActions lastAllowedActions;
	private long lastSelectedId;
	private long[] lastActiveIds;

	private Mask<UnsignedLongType> mask;
	private long currentFillValue;

	private final TLongObjectMap<SelectedObjectInfo> selectedObjects = new TLongObjectHashMap<>();

	private SectionInfo sectionInfo1, sectionInfo2;

	private ModeState modeState;

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
	}

	public ObjectProperty<ViewerPanelFX> activeViewerProperty()
	{
		return activeViewer;
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
						e -> modeState == ModeState.Selecting &&
							!selectedObjects.isEmpty() &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.S)
					)
			);
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"apply mask",
						e -> {e.consume(); applyMask(paintera);},
						e -> modeState == ModeState.Review &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.S)
					)
			);
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"exit shape interpolation mode",
						e -> {e.consume(); exitMode(paintera);},
						e -> isModeOn() && keyTracker.areOnlyTheseKeysDown(KeyCode.ESCAPE)
					)
			);
		filter.addEventHandler(MouseEvent.ANY, new MouseClickFX(
				"select object in current section",
				e -> {e.consume(); selectObject(paintera, e.getX(), e.getY(), true);},
				e -> modeState == ModeState.Selecting && e.isPrimaryButtonDown() && keyTracker.noKeysActive())
			.handler());
		filter.addEventHandler(MouseEvent.ANY, new MouseClickFX(
				"toggle object in current section",
				e -> {e.consume(); selectObject(paintera, e.getX(), e.getY(), false);},
				e -> modeState == ModeState.Selecting &&
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
		setDisableOtherViewers(true);

		lastAllowedActions = paintera.allowedActionsProperty().get();
		paintera.allowedActionsProperty().set(allowedActions);

		try
		{
			createMask();
			lastSelectedId = selectedIds.getLastSelection();
			lastActiveIds = selectedIds.getActiveIds();
			final long newLabelId = mask.info.value.get();
			converter.setColor(newLabelId, MASK_COLOR);
			selectedIds.activate(newLabelId);
		}
		catch (final MaskInUse e)
		{
			e.printStackTrace();
		}

		modeState = ModeState.Selecting;
	}

	public void exitMode(final PainteraBaseView paintera)
	{
		if (!isModeOn())
		{
			LOG.info("Not in shape interpolation mode");
			return;
		}
		LOG.info("Exiting shape interpolation mode");
		setDisableOtherViewers(false);

		paintera.allowedActionsProperty().set(lastAllowedActions);
		lastAllowedActions = null;

		final long newLabelId = mask.info.value.get();
		converter.removeColor(newLabelId);
		selectedIds.activate(lastActiveIds);
		selectedIds.activateAlso(lastSelectedId);
		lastSelectedId = Label.INVALID;
		lastActiveIds = null;
		currentFillValue = 0;
		selectedObjects.clear();
		sectionInfo1 = sectionInfo2 = null;
		modeState = null;
		forgetMask();
		activeViewer.get().requestRepaint();

		activeViewer.set(null);
	}

	public boolean isModeOn()
	{
		return modeState != null;
	}

	private void createMask() throws MaskInUse
	{
		final int time = activeViewer.get().getState().timepointProperty().get();
		final int level = MASK_SCALE_LEVEL;
		final long newLabelId = idService.next();
		final MaskInfo<UnsignedLongType> maskInfo = new MaskInfo<>(time, level, new UnsignedLongType(newLabelId));
		mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
		LOG.info("Generated mask for shape interpolation using new label ID {}", newLabelId);
	}

	private void forgetMask()
	{
		mask = null;
		source.resetMasks();
	}

	private void setDisableOtherViewers(final boolean disable)
	{
		final Parent parent = activeViewer.get().getParent();
		for (final Node child : parent.getChildrenUnmodifiable())
		{
			if (child instanceof ViewerPanelFX && child != activeViewer.get())
			{
				final ViewerPanelFX viewer = (ViewerPanelFX) child;
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
			paintera.allowedActionsProperty().set(allowedActions);
		}
		else
		{
			LOG.debug("Fix selection in the second section");
			sectionInfo2 = createSectionInfo();
			modeState = ModeState.Interpolating;
			interpolateBetweenSections();
			modeState = ModeState.Review;
			paintera.allowedActionsProperty().set(allowedActions);
		}
	}

	private void applyMask(final PainteraBaseView paintera)
	{
		final Interval sectionUnionSourceInterval = Intervals.union(
				sectionInfo1.sourceBoundingBox,
				sectionInfo2.sourceBoundingBox
			);
		System.out.println("applying mask using affected interval of size " + Arrays.toString(Intervals.dimensionsAsLongArray(sectionUnionSourceInterval)));
		source.applyMask(mask, sectionUnionSourceInterval, FOREGROUND_CHECK);
		exitMode(paintera);
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
				getMaskDisplayTransformIgnoreScaling(SHAPE_INTERPOLATION_SCALE_LEVEL),
				selectionSourceBoundingBox
			);
	}

	private void testOutput(final RandomAccessibleInterval<UnsignedLongType> mask, final String dataset)
	{
		try
		{
			N5Utils.save(
					Converters.convert(mask, (in, out) -> out.setInteger(in.get()), new UnsignedShortType()),
					new N5FSWriter("/groups/saalfeld/home/pisarevi/data/paintera-test/test.n5"),
					dataset,
					mask.numDimensions() == 2 ? new int[] {4096, 4096} : new int[] {256, 256, 256},
					new GzipCompression()
				);
		}
		catch (final IOException e)
		{
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private void interpolateBetweenSections()
	{
		final SectionInfo[] sectionInfoPair = {sectionInfo1, sectionInfo2};

		// find the interval that encompasses both of the sections
		final Interval sectionsUnionInterval = Intervals.union(
				getSectionInterval(sectionInfoPair[0]),
				getSectionInterval(sectionInfoPair[1])
			);

		// extract the sections using the union interval
		final RandomAccessibleInterval<UnsignedLongType>[] sectionPair = new RandomAccessibleInterval[2];
		for (int i = 0; i < 2; ++i)
			sectionPair[i] = getMaskSection(sectionInfoPair[i].sourceToDisplayTransform, sectionsUnionInterval);

		// replace fill values in the sections with a single value because we do not want to interpolate between multiple labels
		for (final RandomAccessibleInterval<UnsignedLongType> section : sectionPair)
			for (final UnsignedLongType val : Views.iterable(section))
				if (FOREGROUND_CHECK.test(val))
					val.set(INTERPOLATION_FILL_VALUE);


//		testOutput(sectionPair[0], "section1");
//		testOutput(sectionPair[1], "section2");


		// find the distance between the sections and generate the filler sections between them
		final double distanceBetweenSections = computeDistanceBetweenSections();
		final int numFillers = (int) Math.round(Math.abs(distanceBetweenSections)) - 1;
		final double fillerStep = distanceBetweenSections / (numFillers + 1);
		final RandomAccessibleInterval<UnsignedLongType>[] fillers = new RandomAccessibleInterval[numFillers];
		System.out.println("Distance between the sections: " + distanceBetweenSections);
		System.out.println("Creating " + numFillers + " fillers of size " + Arrays.toString(Intervals.dimensionsAsLongArray(sectionsUnionInterval)) + ":");
		for (int i = 0; i < numFillers; ++i)
		{
			final double fillerShift = fillerStep * (i + 1);
			final AffineTransform3D fillerTransform = sectionInfo1.sourceToDisplayTransform.copy();
			fillerTransform.preConcatenate(new Translation3D(0, 0, fillerShift));
			fillers[i] = getMaskSection(fillerTransform, sectionsUnionInterval);
			System.out.println("  " + fillerShift);
		}


		final List<RandomAccessibleInterval<UnsignedLongType>> sectionsList = new ArrayList<>();
		sectionsList.add(sectionPair[0]);
		sectionsList.addAll(Arrays.asList(fillers));
		sectionsList.add(sectionPair[1]);

		System.out.println("writing debug output before interpolating...");
		testOutput(Views.stack(sectionsList), "sections-before");
		System.out.println("done");

		System.out.println("Running interpolation...");
		InterpolateBetweenSections.interpolateBetweenSections(
				sectionPair[0],
				sectionPair[1],
				new ArrayImgFactory<>(new DoubleType()),
				fillers,
				DISTANCE_TYPE.EUCLIDIAN,
				new double[] {1},
				Label.BACKGROUND
			);
		System.out.println("Done");

		System.out.println("writing debug output after interpolating...");
		testOutput(Views.stack(sectionsList), "sections-after");
		System.out.println("done");
	}

	private Interval getSectionInterval(final SectionInfo sectionInfo)
	{
		final RealInterval sectionBounds = sectionInfo.sourceToDisplayTransform.estimateBounds(sectionInfo.sourceBoundingBox);
		final Interval sectionInterval3D = Intervals.smallestContainingInterval(sectionBounds);
		return new FinalInterval(
				new long[] {sectionInterval3D.min(0), sectionInterval3D.min(1)},
				new long[] {sectionInterval3D.max(0), sectionInterval3D.max(1)}
			);
	}

	private RandomAccessibleInterval<UnsignedLongType> getMaskSection(final AffineTransform3D transform, final Interval sectionInterval)
	{
		final Interval sectionInterval3D = sectionInterval.numDimensions() == 3 ? sectionInterval : new FinalInterval(
				new long[] {sectionInterval.min(0), sectionInterval.min(1), 0l},
				new long[] {sectionInterval.max(0), sectionInterval.max(1), 0l}
			);
		final RealRandomAccessible<UnsignedLongType> transformedMask = getTransformedMask(transform);
		final RandomAccessibleInterval<UnsignedLongType> transformedMaskInterval = Views.interval(Views.raster(transformedMask), sectionInterval3D);
		return Views.hyperSlice(transformedMaskInterval, 2, 0l);
	}

	private RealRandomAccessible<UnsignedLongType> getTransformedMask(final AffineTransform3D transform)
	{
		final RealRandomAccessible<UnsignedLongType> interpolatedMask = Views.interpolate(
				Views.extendValue(mask.mask, new UnsignedLongType(Label.BACKGROUND)),
				new NearestNeighborInterpolatorFactory<>()
			);
		return RealViews.affine(interpolatedMask, transform);
	}

	private double computeDistanceBetweenSections()
	{
		final double[] pos1 = new double[3], pos2 = new double[3];
		sectionInfo1.sourceToDisplayTransform.apply(pos1, pos1);
		sectionInfo2.sourceToDisplayTransform.apply(pos2, pos2);
		return pos2[2] - pos1[2]; // We care only about the shift between the sections (Z distance in the viewer)
	}

	private void selectObject(final PainteraBaseView paintera, final double x, final double y, final boolean deactivateOthers)
	{
		final boolean wasSelected = isSelected(x, y);
		final int numSelectedObjects = selectedObjects.size();

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

	private boolean isSelected(final double x, final double y)
	{
		return FOREGROUND_CHECK.test(getMaskValue(x, y));
	}

	private UnsignedLongType getMaskValue(final double x, final double y)
	{
		final RealPoint sourcePos = getSourceCoordinates(x, y);
		final RandomAccess<UnsignedLongType> maskAccess = mask.mask.randomAccess();
		for (int d = 0; d < sourcePos.numDimensions(); ++d)
			maskAccess.setPosition(Math.round(sourcePos.getDoublePosition(d)), d);
		return maskAccess.get();
	}

	private AffineTransform3D getMaskTransform()
	{
		final AffineTransform3D maskTransform = new AffineTransform3D();
		source.getSourceTransform(mask.info.t, mask.info.level, maskTransform);
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
	private AffineTransform3D getMaskDisplayTransformIgnoreScaling(final int level)
	{
		final AffineTransform3D maskMipmapDisplayTransform = getMaskDisplayTransformIgnoreScaling();
		if (level != mask.info.level)
		{
			// scale with respect to the given mipmap level
			final Scale3D relativeScaleTransform = new Scale3D(DataSource.getRelativeScales(source, mask.info.t, level, mask.info.level));
			maskMipmapDisplayTransform.preConcatenate(relativeScaleTransform);
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
		scalingTransform.concatenate(new Scale3D(DataSource.getScale(source, mask.info.t, mask.info.level)));
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
}
