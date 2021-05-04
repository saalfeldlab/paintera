package org.janelia.saalfeldlab.paintera.control;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.util.Affine3DHelpers;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
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
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.event.MouseClickFX;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.NamedKeyCombination;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions.AllowedActionsBuilder;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActionsProperty;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D;
import org.janelia.saalfeldlab.paintera.control.paint.PaintUtils;
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

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.Predicate;

public class ShapeInterpolationMode<D extends IntegerType<D>> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static enum ModeState {
	Select,
	Interpolate,
	Preview
  }

  public static enum ActiveSection {
	First("1"),
	Second("2");

	private final String s;

	private ActiveSection(final String s) {

	  this.s = s;
	}

	@Override
	public String toString() {

	  return s;
	}
  }

  private static final class SelectedObjectInfo {

	final RealPoint sourceClickPosition;
	final Interval sourceBoundingBox;

	SelectedObjectInfo(final RealPoint sourceClickPosition, final Interval sourceBoundingBox) {

	  this.sourceClickPosition = sourceClickPosition;
	  this.sourceBoundingBox = sourceBoundingBox;
	}
  }

  private static final class SectionInfo {

	final Mask<UnsignedLongType> mask;
	final AffineTransform3D globalTransform;
	final AffineTransform3D sourceToDisplayTransform;
	final Interval sourceBoundingBox;
	final TLongObjectMap<SelectedObjectInfo> selectedObjects;

	SectionInfo(
			final Mask<UnsignedLongType> mask,
			final AffineTransform3D globalTransform,
			final AffineTransform3D sourceToDisplayTransform,
			final Interval sourceBoundingBox,
			final TLongObjectMap<SelectedObjectInfo> selectedObjects) {

	  this.mask = mask;
	  this.globalTransform = globalTransform;
	  this.sourceToDisplayTransform = sourceToDisplayTransform;
	  this.sourceBoundingBox = sourceBoundingBox;
	  this.selectedObjects = selectedObjects;
	}
  }

  private static final double FILL_DEPTH = 2.0;

  private static final double FILL_DEPTH_ORTHOGONAL = 1.0;

  private static final int MASK_SCALE_LEVEL = 0;

  private static final int SHAPE_INTERPOLATION_SCALE_LEVEL = MASK_SCALE_LEVEL;

  private static final Color MASK_COLOR = Color.web("00CCFF");

  private static final Predicate<UnsignedLongType> FOREGROUND_CHECK = t -> Label.isForeground(t.get());

  private final MaskedSource<D, ?> source;
  private final Runnable refreshMeshes;
  private final SelectedIds selectedIds;
  private final IdService idService;
  private final HighlightingStreamConverter<?> converter;
  private final FragmentSegmentAssignment assignment;

  private ViewerPanelFX activeViewer;
  private ChangeListener<AllowedActions> modeSwitchListener;
  private AllowedActions lastAllowedActions;
  private long lastSelectedId;
  private long[] lastActiveIds;

  private final ChangeListener<Boolean> doneApplyingMaskListener;
  private Mask<UnsignedLongType> mask;
  private long newLabelId;
  private long currentFillValue;

  private final TLongObjectMap<SelectedObjectInfo> selectedObjects = new TLongObjectHashMap<>();

  private final ObjectProperty<SectionInfo> sectionInfo1 = new SimpleObjectProperty<>();
  private final ObjectProperty<SectionInfo> sectionInfo2 = new SimpleObjectProperty<>();

  private final ObjectProperty<ModeState> modeState = new SimpleObjectProperty<>();
  private final ObjectProperty<ActiveSection> activeSection = new SimpleObjectProperty<>();

  private Thread workerThread;
  private Runnable onInterpolationFinished;
  private Pair<RealRandomAccessible<UnsignedLongType>, RealRandomAccessible<VolatileUnsignedLongType>> interpolatedMaskImgs;

  public ShapeInterpolationMode(
		  final MaskedSource<D, ?> source,
		  final Runnable refreshMeshes,
		  final SelectedIds selectedIds,
		  final IdService idService,
		  final HighlightingStreamConverter<?> converter,
		  final FragmentSegmentAssignment assignment) {

	this.source = source;
	this.refreshMeshes = refreshMeshes;
	this.selectedIds = selectedIds;
	this.idService = idService;
	this.converter = converter;
	this.assignment = assignment;

	this.doneApplyingMaskListener = (obs, oldv, newv) -> {
	  if (!newv)
		InvokeOnJavaFXApplicationThread.invoke(this::doneApplyingMask);
	};
  }

  public ObjectProperty<ModeState> modeStateProperty() {

	return modeState;
  }

  public ObjectProperty<ActiveSection> activeSectionProperty() {

	return activeSection;
  }

  public EventHandler<Event> modeHandler(
		  final PainteraBaseView paintera,
		  final KeyTracker keyTracker,
		  final KeyAndMouseBindings bindings,
		  final String bindingKeyEnterShapeInterpolationMode,
		  final String bindingKeyExitShapeInterpolationMode,
		  final String bindingKeyShapeInterpolationApplyMask,
		  final String bindingKeyShapeInterpolationEditSelection1,
		  final String bindingKeyShapeInterpolationEditSelection2) {

	final NamedKeyCombination.CombinationMap keyCombinations = bindings.getKeyCombinations();
	final DelegateEventHandlers.AnyHandler filter = DelegateEventHandlers.handleAny();
	filter.addEventHandler(
			KeyEvent.KEY_PRESSED,
			EventFX.KEY_PRESSED(
					bindingKeyEnterShapeInterpolationMode,
					e -> {
					  e.consume();
					  enterMode(paintera, (ViewerPanelFX)e.getTarget());
					},
					e -> e.getTarget() instanceof ViewerPanelFX &&
							!isModeOn() &&
							source.getCurrentMask() == null &&
							!source.isApplyingMaskProperty().get() &&
							keyCombinations.matches(bindingKeyEnterShapeInterpolationMode, e)));
	filter.addEventHandler(
			KeyEvent.KEY_PRESSED,
			EventFX.KEY_PRESSED(
					bindingKeyExitShapeInterpolationMode,
					e -> {
					  e.consume();
					  exitMode(paintera, false);
					},
					e -> isModeOn() && keyCombinations.matches(bindingKeyExitShapeInterpolationMode, e)));
	filter.addEventHandler(
			KeyEvent.KEY_PRESSED,
			EventFX.KEY_PRESSED(
					bindingKeyShapeInterpolationApplyMask,
					e -> {
					  e.consume();
					  applyMask(paintera);
					},
					e -> isModeOn() && keyCombinations.matches(bindingKeyShapeInterpolationApplyMask, e)));
	filter.addEventHandler(
			KeyEvent.KEY_PRESSED,
			EventFX.KEY_PRESSED(
					bindingKeyShapeInterpolationEditSelection1,
					e -> {
					  e.consume();
					  editSelection(paintera, ActiveSection.First);
					},
					e -> keyCombinations.matches(bindingKeyShapeInterpolationEditSelection1, e)));
	filter.addEventHandler(
			KeyEvent.KEY_PRESSED,
			EventFX.KEY_PRESSED(
					bindingKeyShapeInterpolationEditSelection2,
					e -> {
					  e.consume();
					  editSelection(paintera, ActiveSection.Second);
					},
					e -> keyCombinations.matches(bindingKeyShapeInterpolationEditSelection2, e)));

	filter.addEventHandler(MouseEvent.ANY, new MouseClickFX(
			"select object in current section",
			e -> {
			  e.consume();
			  selectObject(paintera, e.getX(), e.getY(), true);
			},
			e -> modeState.get() == ModeState.Select && e.isPrimaryButtonDown() && keyTracker.noKeysActive())
			.handler());
	filter.addEventHandler(MouseEvent.ANY, new MouseClickFX(
			"toggle object in current section",
			e -> {
			  e.consume();
			  selectObject(paintera, e.getX(), e.getY(), false);
			},
			e -> modeState.get() == ModeState.Select &&
					((e.isSecondaryButtonDown() && keyTracker.noKeysActive()) ||
							(e.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL))))
			.handler());

	return filter;
  }

  public void enterMode(final PainteraBaseView paintera, final ViewerPanelFX viewer) {

	if (isModeOn()) {
	  LOG.info("Already in shape interpolation mode");
	  return;
	}
	LOG.info("Entering shape interpolation mode");
	activeViewer = viewer;
	setDisableOtherViewers(paintera, true);

	// set allowed actions in this mode
	final AllowedActionsBuilder allowedActionsBuilder = new AllowedActionsBuilder();
	allowedActionsBuilder.add(NavigationActionType.Drag, NavigationActionType.Zoom, MenuActionType.ToggleMaximizeViewer);
	allowedActionsBuilder.add(NavigationActionType.Scroll, () -> {
	  // allow to scroll through sections, but fix the selection first if the object selection is not empty
	  if (modeState.get() == ModeState.Select && !selectedObjects.isEmpty()) {
		fixSelection(paintera);
		advanceMode(paintera);
	  }
	  return true;
	});
	lastAllowedActions = paintera.allowedActionsProperty().get();
	paintera.allowedActionsProperty().set(allowedActionsBuilder.create());

	// properly exit the mode if somebody else wants to switch it
	modeSwitchListener = (obs, oldv, newv) -> {
	  if (!((AllowedActionsProperty)obs).isProcessingEnableDisable()) {
		/* we don't want to exit accidentally during the disable/enable that happens during mask creation.
		 We only want to exit if this was intentionally change (and enabled)*/
		exitMode(paintera, false);
	  }
	};
	paintera.allowedActionsProperty().addListener(modeSwitchListener);

	lastSelectedId = selectedIds.getLastSelection();
	lastActiveIds = selectedIds.getActiveIdsCopyAsArray();
	newLabelId = idService.next();
	converter.setColor(newLabelId, MASK_COLOR);
	selectedIds.activate(newLabelId);

	activeSection.set(ActiveSection.First);
	modeState.set(ModeState.Select);
  }

  public void exitMode(final PainteraBaseView paintera, final boolean completed) {

	if (!isModeOn()) {
	  LOG.info("Not in shape interpolation mode");
	  return;
	}
	LOG.info("Exiting shape interpolation mode");
	setDisableOtherViewers(paintera, false);

	if (!completed) // extra cleanup if the mode is aborted
	{
	  interruptInterpolation();
	  resetMask();
	}

	converter.removeColor(newLabelId);
	newLabelId = Label.INVALID;

	selectedIds.activate(lastActiveIds);
	selectedIds.activateAlso(lastSelectedId);

	paintera.allowedActionsProperty().removeListener(modeSwitchListener);
	modeSwitchListener = null;

	paintera.allowedActionsProperty().enable();
	paintera.allowedActionsProperty().set(lastAllowedActions);
	lastAllowedActions = null;

	currentFillValue = 0;
	selectedObjects.clear();
	sectionInfo1.set(null);
	sectionInfo2.set(null);
	modeState.set(null);
	activeSection.set(null);
	mask = null;

	workerThread = null;
	onInterpolationFinished = null;
	interpolatedMaskImgs = null;
	lastSelectedId = Label.INVALID;
	lastActiveIds = null;

	paintera.orthogonalViews().requestRepaint();
	activeViewer = null;
  }

  public boolean isModeOn() {

	return modeState.get() != null;
  }

  private void createMask() throws MaskInUse {

	final int time = activeViewer.getState().getTimepoint();
	final int level = MASK_SCALE_LEVEL;
	final MaskInfo<UnsignedLongType> maskInfo = new MaskInfo<>(time, level, new UnsignedLongType(newLabelId));
	mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
  }

  private void resetMask() {

	try {
	  source.resetMasks();
	} catch (final MaskInUse e) {
	  e.printStackTrace();
	}
	mask = null;
  }

  private void setDisableOtherViewers(final PainteraBaseView paintera, final boolean disable) {

	final ViewerPanelFX[] viewers = {
			paintera.orthogonalViews().topLeft().viewer(),
			paintera.orthogonalViews().topRight().viewer(),
			paintera.orthogonalViews().bottomLeft().viewer()
	};

	for (final ViewerPanelFX viewer : viewers) {
	  if (viewer != activeViewer) {
		viewer.setDisable(disable);
		if (disable) {
		  final ColorAdjust grayedOutEffect = new ColorAdjust();
		  grayedOutEffect.setContrast(-0.2);
		  grayedOutEffect.setBrightness(-0.5);
		  viewer.setEffect(grayedOutEffect);
		} else {
		  viewer.setEffect(null);
		}
	  }
	}
  }

  private void fixSelection(final PainteraBaseView paintera) {

	final ObjectProperty<SectionInfo> sectionInfoPropertyToSet = activeSection.get() == ActiveSection.First ? sectionInfo1 : sectionInfo2;
	LOG.debug("Fix selection");
	sectionInfoPropertyToSet.set(createSectionInfo(paintera));
	selectedObjects.clear();
  }

  private void advanceMode(final PainteraBaseView paintera) {

	if (sectionInfo1.get() == null || sectionInfo2.get() == null) {
	  // let the user now select the second section
	  activeSection.set(ActiveSection.Second);
	  resetMask();
	  paintera.orthogonalViews().requestRepaint();
	} else {
	  // both sections are ready, run interpolation
	  activeSection.set(null);
	  modeState.set(ModeState.Interpolate);
	  onInterpolationFinished = () -> modeState.set(ModeState.Preview);
	  interpolateBetweenSections(paintera);
	}
  }

  private void editSelection(final PainteraBaseView paintera, final ActiveSection section) {

	interruptInterpolation();

	if (activeSection.get() == section)
	  return;

	if (activeSection.get() != null) {
	  if (selectedObjects.isEmpty())
		return;
	  fixSelection(paintera);
	}

	final ObjectProperty<SectionInfo> sectionInfoPropertyToEdit = section == ActiveSection.First ? sectionInfo1 : sectionInfo2;
	final SectionInfo sectionInfo = sectionInfoPropertyToEdit.get();

	if (sectionInfo == null) {
	  advanceMode(paintera);
	  return;
	}

	resetMask();
	try {
	  source.setMask(sectionInfo.mask, FOREGROUND_CHECK);
	} catch (final MaskInUse e) {
	  e.printStackTrace();
	}
	mask = sectionInfo.mask;

	paintera.manager().setTransform(sectionInfo.globalTransform);

	selectedObjects.clear();
	selectedObjects.putAll(sectionInfo.selectedObjects);

	sectionInfoPropertyToEdit.set(null);
	activeSection.set(section);

	modeState.set(ModeState.Select);
  }

  private void applyMask(final PainteraBaseView paintera) {

	if (modeState.get() == ModeState.Select) {
	  final boolean firstSectionReady = sectionInfo1.get() != null || (activeSection.get() == ActiveSection.First && !selectedObjects.isEmpty());
	  final boolean secondSectionReady = sectionInfo2.get() != null || (activeSection.get() == ActiveSection.Second && !selectedObjects.isEmpty());
	  if (!firstSectionReady || !secondSectionReady)
		return;

	  fixSelection(paintera);
	  advanceMode(paintera);
	}

	if (modeState.get() == ModeState.Interpolate) {
	  // wait until the interpolation is done
	  try {
		workerThread.join();
	  } catch (final InterruptedException e) {
		e.printStackTrace();
	  }
	  runOnInterpolationFinished();
	}

	assert modeState.get() == ModeState.Preview;

	final Interval sectionsUnionSourceInterval = Intervals.union(
			sectionInfo1.get().sourceBoundingBox,
			sectionInfo2.get().sourceBoundingBox
	);
	LOG.info("Applying interpolated mask using bounding box of size {}", Intervals.dimensionsAsLongArray(sectionsUnionSourceInterval));

	if (Label.regular(lastSelectedId)) {
	  final MaskInfo<UnsignedLongType> maskInfoWithLastSelectedLabelId = new MaskInfo<>(
			  source.getCurrentMask().info.t,
			  source.getCurrentMask().info.level,
			  new UnsignedLongType(lastSelectedId)
	  );
	  resetMask();
	  try {
		source.setMask(maskInfoWithLastSelectedLabelId, interpolatedMaskImgs.getA(), interpolatedMaskImgs.getB(), null, null, null, FOREGROUND_CHECK);
	  } catch (final MaskInUse e) {
		e.printStackTrace();
	  }
	} else {
	  lastSelectedId = newLabelId;
	}

	source.isApplyingMaskProperty().addListener(doneApplyingMaskListener);
	source.applyMask(source.getCurrentMask(), sectionsUnionSourceInterval, FOREGROUND_CHECK);

	exitMode(paintera, true);
  }

  private void doneApplyingMask() {
	// generate mesh for the interpolated shape
	source.isApplyingMaskProperty().removeListener(doneApplyingMaskListener);
	refreshMeshes.run();
  }

  private SectionInfo createSectionInfo(final PainteraBaseView paintera) {

	Interval selectionSourceBoundingBox = null;
	for (final TLongObjectIterator<SelectedObjectInfo> it = selectedObjects.iterator(); it.hasNext(); ) {
	  it.advance();
	  if (selectionSourceBoundingBox == null)
		selectionSourceBoundingBox = it.value().sourceBoundingBox;
	  else
		selectionSourceBoundingBox = Intervals.union(selectionSourceBoundingBox, it.value().sourceBoundingBox);
	}

	final AffineTransform3D globalTransform = new AffineTransform3D();
	paintera.manager().getTransform(globalTransform);

	return new SectionInfo(
			mask,
			globalTransform,
			getMaskDisplayTransformIgnoreScaling(SHAPE_INTERPOLATION_SCALE_LEVEL),
			selectionSourceBoundingBox,
			new TLongObjectHashMap<>(selectedObjects)
	);
  }

  @SuppressWarnings("unchecked")
  private void interpolateBetweenSections(final PainteraBaseView paintera) {

	workerThread = new Thread(() ->
	{
	  final SectionInfo[] sectionInfoPair = {sectionInfo1.get(), sectionInfo2.get()};

	  final Interval affectedUnionSourceInterval = Intervals.union(
			  sectionInfoPair[0].sourceBoundingBox,
			  sectionInfoPair[1].sourceBoundingBox
	  );

	  // get the two sections as 2D images
	  final Interval[] displaySectionIntervalPair = new Interval[2];
	  final RandomAccessibleInterval<UnsignedLongType>[] sectionPair = new RandomAccessibleInterval[2];
	  for (int i = 0; i < 2; ++i) {
		final SectionInfo newSectionInfo = new SectionInfo(
				sectionInfoPair[i].mask,
				sectionInfoPair[i].globalTransform,
				sectionInfoPair[i].sourceToDisplayTransform,
				affectedUnionSourceInterval,
				sectionInfoPair[i].selectedObjects
		);
		final RandomAccessibleInterval<UnsignedLongType> section = getTransformedMaskSection(newSectionInfo);
		displaySectionIntervalPair[i] = new FinalInterval(section);
		sectionPair[i] = Views.zeroMin(section);
	  }

	  // Narrow the bounding box of the two sections in the display space.
	  // The initial bounding box may be larger because of transforming the source bounding box into the display space and then taking the bounding box of that.
	  final Interval[] boundingBoxPair = new Interval[2];
	  for (int i = 0; i < 2; ++i) {
		if (Thread.currentThread().isInterrupted())
		  return;

		final long[] min = new long[2], max = new long[2], position = new long[2];
		Arrays.fill(min, Long.MAX_VALUE);
		Arrays.fill(max, Long.MIN_VALUE);
		final Cursor<UnsignedLongType> cursor = Views.iterable(sectionPair[i]).localizingCursor();
		while (cursor.hasNext()) {
		  if (FOREGROUND_CHECK.test(cursor.next())) {
			cursor.localize(position);
			for (int d = 0; d < position.length; ++d) {
			  min[d] = Math.min(min[d], position[d]);
			  max[d] = Math.max(max[d], position[d]);
			}
		  }
		}
		boundingBoxPair[i] = new FinalInterval(min, max);
	  }
	  final Interval boundingBox = Intervals.union(boundingBoxPair[0], boundingBoxPair[1]);
	  LOG.debug("Narrowed the bounding box of the selected shape in both sections from {} to {}", Intervals.dimensionsAsLongArray(sectionPair[0]),
			  Intervals.dimensionsAsLongArray(boundingBox));
	  for (int i = 0; i < 2; ++i) {
		sectionPair[i] = Views.offsetInterval(sectionPair[i], boundingBox);
	  }

	  // compute distance transform on both sections
	  final RandomAccessibleInterval<FloatType>[] distanceTransformPair = new RandomAccessibleInterval[2];
	  for (int i = 0; i < 2; ++i) {
		if (Thread.currentThread().isInterrupted())
		  return;

		distanceTransformPair[i] = new ArrayImgFactory<>(new FloatType()).create(sectionPair[i]);
		final RandomAccessibleInterval<BoolType> binarySection = Converters.convert(sectionPair[i], new PredicateConverter<>(FOREGROUND_CHECK), new BoolType());
		computeSignedDistanceTransform(binarySection, distanceTransformPair[i], DISTANCE_TYPE.EUCLIDIAN);
	  }

	  final double distanceBetweenSections = computeDistanceBetweenSections(sectionInfoPair[0], sectionInfoPair[1]);
	  final AffineTransform3D transformToSource = new AffineTransform3D();
	  transformToSource
			  .preConcatenate(new Translation3D(boundingBox.min(0), boundingBox.min(1), 0))
			  .preConcatenate(new Translation3D(displaySectionIntervalPair[0].min(0), displaySectionIntervalPair[0].min(1), 0))
			  .preConcatenate(sectionInfoPair[0].sourceToDisplayTransform.inverse());

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

	  try {
		synchronized (source) {
		  final MaskInfo<UnsignedLongType> maskInfo = mask.info;
		  resetMask();
		  source.setMask(
				  maskInfo,
				  interpolatedShapeMask,
				  volatileInterpolatedShapeMask,
				  null,
				  null,
				  null,
				  FOREGROUND_CHECK);
		  interpolatedMaskImgs = new ValuePair<>(interpolatedShapeMask, volatileInterpolatedShapeMask);
		}

		paintera.orthogonalViews().requestRepaint();
	  } catch (final MaskInUse e) {
		LOG.error("Label source already has an active mask");
	  }

	  InvokeOnJavaFXApplicationThread.invoke(this::runOnInterpolationFinished);
	});
	workerThread.start();
  }

  private void runOnInterpolationFinished() {

	if (onInterpolationFinished != null) {
	  onInterpolationFinished.run();
	  onInterpolationFinished = null;
	}
  }

  private void interruptInterpolation() {

	if (workerThread != null) {
	  workerThread.interrupt();
	  try {
		workerThread.join();
	  } catch (final InterruptedException e) {
		e.printStackTrace();
	  }
	}
	onInterpolationFinished = null;
  }

  private static <R extends RealType<R> & NativeType<R>, B extends BooleanType<B>> void computeSignedDistanceTransform(
		  final RandomAccessibleInterval<B> mask,
		  final RandomAccessibleInterval<R> target,
		  final DISTANCE_TYPE distanceType,
		  final double... weights) {

	final RandomAccessibleInterval<R> distanceOutside = target;
	final RandomAccessibleInterval<R> distanceInside = new ArrayImgFactory<>(Util.getTypeFromInterval(target)).create(target);
	DistanceTransform.binaryTransform(mask, distanceOutside, distanceType, weights);
	DistanceTransform.binaryTransform(Logical.complement(mask), distanceInside, distanceType, weights);
	LoopBuilder.setImages(distanceOutside, distanceInside, target).forEachPixel((outside, inside, result) -> {
	  switch (distanceType) {
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
		  final AffineTransform3D transformToSource) {

	final RandomAccessibleInterval<R> distanceTransformStack = Views.stack(dt1, dt2);

	final R extendValue = Util.getTypeFromInterval(distanceTransformStack).createVariable();
	extendValue.setReal(extendValue.getMaxValue());
	final RealRandomAccessible<R> interpolatedDistanceTransform = Views.interpolate(
			Views.extendValue(distanceTransformStack, extendValue),
			new NLinearInterpolatorFactory<>()
	);

	final RealRandomAccessible<R> scaledInterpolatedDistanceTransform = RealViews.affineReal(
			interpolatedDistanceTransform,
			new Scale3D(1, 1, -distance)
	);

	final T emptyValue = targetValue.createVariable();
	final RealRandomAccessible<T> interpolatedShape = Converters.convert(
			scaledInterpolatedDistanceTransform,
			(in, out) -> out.set(in.getRealDouble() <= 0 ? targetValue : emptyValue),
			emptyValue.createVariable()
	);

	return RealViews.affineReal(interpolatedShape, transformToSource);
  }

  private RandomAccessibleInterval<UnsignedLongType> getTransformedMaskSection(final SectionInfo sectionInfo) {

	final RealInterval sectionBounds = sectionInfo.sourceToDisplayTransform.estimateBounds(sectionInfo.sourceBoundingBox);
	final Interval sectionInterval = Intervals.smallestContainingInterval(sectionBounds);
	final RealRandomAccessible<UnsignedLongType> transformedMask = getTransformedMask(sectionInfo.mask, sectionInfo.sourceToDisplayTransform);
	final RandomAccessibleInterval<UnsignedLongType> transformedMaskInterval = Views.interval(Views.raster(transformedMask), sectionInterval);
	return Views.hyperSlice(transformedMaskInterval, 2, 0l);
  }

  private static RealRandomAccessible<UnsignedLongType> getTransformedMask(final Mask<UnsignedLongType> mask, final AffineTransform3D transform) {

	final RealRandomAccessible<UnsignedLongType> interpolatedMask = Views.interpolate(
			Views.extendValue(mask.mask, new UnsignedLongType(Label.OUTSIDE)),
			new NearestNeighborInterpolatorFactory<>()
	);
	return RealViews.affine(interpolatedMask, transform);
  }

  private static double computeDistanceBetweenSections(final SectionInfo s1, final SectionInfo s2) {

	final double[] pos1 = new double[3], pos2 = new double[3];
	s1.sourceToDisplayTransform.apply(pos1, pos1);
	s2.sourceToDisplayTransform.apply(pos2, pos2);
	return pos2[2] - pos1[2]; // We care only about the shift between the sections (Z distance in the viewer)
  }

  private void selectObject(final PainteraBaseView paintera, final double x, final double y, final boolean deactivateOthers) {
	// create the mask if needed
	if (mask == null) {
	  LOG.debug("No selected objects yet, create mask");
	  try {
		createMask();
	  } catch (final MaskInUse e) {
		e.printStackTrace();
	  }
	}

	final UnsignedLongType maskValue = getMaskValue(x, y);
	if (maskValue.get() == Label.OUTSIDE)
	  return;

	// ignore the background label
	final D dataValue = getDataValue(x, y);
	if (!FOREGROUND_CHECK.test(new UnsignedLongType(dataValue.getIntegerLong())))
	  return;

	final boolean wasSelected = FOREGROUND_CHECK.test(maskValue);
	LOG.debug("Object was clicked: deactivateOthers={}, wasSelected={}", deactivateOthers, wasSelected);

	if (deactivateOthers) {
	  // If the clicked object is not selected, deselect all other objects and select the clicked object.
	  // If the clicked object is the only selected object, toggle it.
	  // If the clicked object is selected along with some other objects, deselect the others and keep the clicked one selected.
	  final boolean keepClickedObjectSelected = wasSelected && selectedObjects.size() > 1;
	  for (final TLongObjectIterator<SelectedObjectInfo> it = selectedObjects.iterator(); it.hasNext(); ) {
		it.advance();
		final double[] deselectDisplayPos = getDisplayCoordinates(it.value().sourceClickPosition);
		if (!keepClickedObjectSelected || !getMaskValue(deselectDisplayPos[0], deselectDisplayPos[1]).valueEquals(maskValue)) {
		  runFloodFillToDeselect(deselectDisplayPos[0], deselectDisplayPos[1]);
		  it.remove();
		}
	  }
	  if (!wasSelected) {
		final Pair<Long, Interval> fillValueAndInterval = runFloodFillToSelect(x, y);
		selectedObjects.put(fillValueAndInterval.getA(), new SelectedObjectInfo(getSourceCoordinates(x, y), fillValueAndInterval.getB()));
	  }
	} else {
	  // Simply toggle the clicked object
	  if (!wasSelected) {
		final Pair<Long, Interval> fillValueAndInterval = runFloodFillToSelect(x, y);
		selectedObjects.put(fillValueAndInterval.getA(), new SelectedObjectInfo(getSourceCoordinates(x, y), fillValueAndInterval.getB()));
	  } else {
		final long oldFillValue = runFloodFillToDeselect(x, y);
		selectedObjects.remove(oldFillValue);
	  }
	}

	// free the mask if there are no selected objects
	if (selectedObjects.isEmpty()) {
	  LOG.debug("No selected objects, reset mask");
	  resetMask();
	}

	paintera.orthogonalViews().requestRepaint();
  }

  /**
   * Flood-fills the mask using a new fill value to mark the object as selected.
   *
   * @param x
   * @param y
   * @return the fill value of the selected object and the affected interval in source coordinates
   */
  private Pair<Long, Interval> runFloodFillToSelect(final double x, final double y) {

	final long fillValue = ++currentFillValue;
	final double fillDepth = determineFillDepth();
	LOG.debug("Flood-filling to select object: fill value={}, depth={}", fillValue, fillDepth);
	final Interval affectedInterval = FloodFill2D.fillMaskAt(x, y, activeViewer, mask, source, assignment, fillValue, fillDepth);
	return new ValuePair<>(fillValue, affectedInterval);
  }

  /**
   * Flood-fills the mask using the background value to remove the object from the selection.
   *
   * @param x
   * @param y
   * @return the fill value of the deselected object
   */
  private long runFloodFillToDeselect(final double x, final double y) {
	// set the predicate to accept only the fill value at the clicked location to avoid deselecting adjacent objects.
	final long maskValue = getMaskValue(x, y).get();
	final RandomAccessibleInterval<BoolType> predicate = Converters.convert(
			mask.mask,
			(in, out) -> out.set(in.getIntegerLong() == maskValue),
			new BoolType()
	);
	final double fillDepth = determineFillDepth();
	LOG.debug("Flood-filling to deselect object: old value={}, depth={}", maskValue, fillDepth);
	FloodFill2D.fillMaskAt(x, y, activeViewer, mask, predicate, getMaskTransform(), Label.BACKGROUND, determineFillDepth());
	return maskValue;
  }

  private double determineFillDepth() {

	final int normalAxis = PaintUtils.labelAxisCorrespondingToViewerAxis(getMaskTransform(), getDisplayTransform(), 2);
	return normalAxis < 0 ? FILL_DEPTH : FILL_DEPTH_ORTHOGONAL;
  }

  private UnsignedLongType getMaskValue(final double x, final double y) {

	final RealPoint sourcePos = getSourceCoordinates(x, y);
	final RandomAccess<UnsignedLongType> maskAccess = Views.extendValue(mask.mask, new UnsignedLongType(Label.OUTSIDE)).randomAccess();
	for (int d = 0; d < sourcePos.numDimensions(); ++d) {
	  maskAccess.setPosition(Math.round(sourcePos.getDoublePosition(d)), d);
	}
	return maskAccess.get();
  }

  private D getDataValue(final double x, final double y) {

	final RealPoint sourcePos = getSourceCoordinates(x, y);
	final int time = activeViewer.getState().getTimepoint();
	final int level = MASK_SCALE_LEVEL;
	final RandomAccessibleInterval<D> data = source.getDataSource(time, level);
	final RandomAccess<D> dataAccess = data.randomAccess();
	for (int d = 0; d < sourcePos.numDimensions(); ++d) {
	  dataAccess.setPosition(Math.round(sourcePos.getDoublePosition(d)), d);
	}
	return dataAccess.get();
  }

  private AffineTransform3D getMaskTransform() {

	final AffineTransform3D maskTransform = new AffineTransform3D();
	final int time = activeViewer.getState().getTimepoint();
	final int level = MASK_SCALE_LEVEL;
	source.getSourceTransform(time, level, maskTransform);
	return maskTransform;
  }

  private AffineTransform3D getDisplayTransform() {

	final AffineTransform3D viewerTransform = new AffineTransform3D();
	activeViewer.getState().getViewerTransform(viewerTransform);
	return viewerTransform;
  }

  private AffineTransform3D getMaskDisplayTransform() {

	return getDisplayTransform().concatenate(getMaskTransform());
  }

  /**
   * Returns the transformation to bring the mask to the current viewer plane at the requested mipmap level.
   * Ignores the scaling in the viewer and in the mask and instead uses the requested mipmap level for scaling.
   *
   * @param targetLevel
   * @return
   */
  private AffineTransform3D getMaskDisplayTransformIgnoreScaling(final int targetLevel) {

	final AffineTransform3D maskMipmapDisplayTransform = getMaskDisplayTransformIgnoreScaling();
	final int maskLevel = MASK_SCALE_LEVEL;
	if (targetLevel != maskLevel) {
	  // scale with respect to the given mipmap level
	  final int time = activeViewer.getState().getTimepoint();
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
  private AffineTransform3D getMaskDisplayTransformIgnoreScaling() {

	final AffineTransform3D viewerTransform = getDisplayTransform();
	// undo scaling in the viewer
	final double[] viewerScale = new double[viewerTransform.numDimensions()];
	Arrays.setAll(viewerScale, d -> Affine3DHelpers.extractScale(viewerTransform, d));
	final Scale3D scalingTransform = new Scale3D(viewerScale);
	// neutralize mask scaling if there is any
	final int time = activeViewer.getState().getTimepoint();
	final int level = MASK_SCALE_LEVEL;
	scalingTransform.concatenate(new Scale3D(DataSource.getScale(source, time, level)));
	// build the resulting transform
	return viewerTransform.preConcatenate(scalingTransform.inverse()).concatenate(getMaskTransform());
  }

  private RealPoint getSourceCoordinates(final double x, final double y) {

	final AffineTransform3D maskTransform = getMaskTransform();
	final RealPoint sourcePos = new RealPoint(maskTransform.numDimensions());
	activeViewer.displayToSourceCoordinates(x, y, maskTransform, sourcePos);
	return sourcePos;
  }

  private double[] getDisplayCoordinates(final RealPoint sourcePos) {

	final AffineTransform3D maskDisplayTransform = getMaskDisplayTransform();
	final RealPoint displayPos = new RealPoint(maskDisplayTransform.numDimensions());
	maskDisplayTransform.apply(sourcePos, displayPos);
	assert Util.isApproxEqual(displayPos.getDoublePosition(2), 0.0, 1e-10);
	return new double[]{displayPos.getDoublePosition(0), displayPos.getDoublePosition(1)};
  }
}
