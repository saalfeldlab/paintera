package org.janelia.saalfeldlab.paintera.state;

import bdv.util.volatiles.VolatileTypeMatcher;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Invalidate;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys;
import org.janelia.saalfeldlab.paintera.NamedKeyCombination;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.cache.NoOpInvalidate;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings;
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationMode;
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationMode.ActiveSection;
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationMode.ModeState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState;
import org.janelia.saalfeldlab.paintera.control.selection.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.PredicateDataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.LocalIdService;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.FragmentLabelMeshCacheKey;
import org.janelia.saalfeldlab.paintera.stream.ARGBStreamSeedSetter;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterIntegerType;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.ShowOnlySelectedInStreamToggle;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.LongFunction;

@Deprecated
public class LabelSourceState<D extends IntegerType<D>, T extends Volatile<D> & Type<T>>
		extends MinimalSourceState<D, T, DataSource<D, T>, HighlightingStreamConverter<T>>
		implements IntersectableSourceState<D, T, FragmentLabelMeshCacheKey> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final LongFunction<Converter<D, BoolType>> maskForLabel;

  private final FragmentSegmentAssignmentState assignment;

  private final SelectedIds selectedIds;

  private final IdService idService;

  private final MeshManagerWithAssignmentForSegments meshManager;

  private final LockedSegmentsState lockedSegments;

  private final LabelBlockLookup labelBlockLookup;

  private final LabelSourceStatePaintHandler<D> paintHandler;

  private final LabelSourceStateIdSelectorHandler idSelectorHandler;

  private final LabelSourceStateMergeDetachHandler mergeDetachHandler;

  private final ShapeInterpolationMode<D> shapeInterpolationMode;

  private final LabelSourceStateCommitHandler commitHandler;

  private final ObjectProperty<FloodFillState> floodFillState = new SimpleObjectProperty<>();

  private final ARGBStreamSeedSetter streamSeedSetter;

  private final ShowOnlySelectedInStreamToggle showOnlySelectedInStreamToggle;

  private final HBox displayStatus;

  public LabelSourceState(
		  final DataSource<D, T> dataSource,
		  final HighlightingStreamConverter<T> converter,
		  final Composite<ARGBType, ARGBType> composite,
		  final String name,
		  final FragmentSegmentAssignmentState assignment,
		  final LockedSegmentsState lockedSegments,
		  final IdService idService,
		  final SelectedIds selectedIds,
		  final MeshManagerWithAssignmentForSegments meshManager,
		  final LabelBlockLookup labelBlockLookup) {

	super(dataSource, converter, composite, name);
	LOG.warn("Using deprecated class LabelSourceState. Use ConnectomicsLabelState instead.");
	final D d = dataSource.getDataType();
	this.maskForLabel = equalsMaskForType(d);
	this.assignment = assignment;
	this.lockedSegments = lockedSegments;
	this.selectedIds = selectedIds;
	this.idService = idService;
	this.meshManager = meshManager;
	this.labelBlockLookup = labelBlockLookup;
	this.paintHandler = dataSource instanceof MaskedSource<?, ?>
			? new LabelSourceStatePaintHandler<D>(
			(MaskedSource<D, ?>)dataSource,
			assignment,
			this.isVisibleProperty()::get,
			this.floodFillState::setValue,
			selectedIds,
			maskForLabel)
			: null;
	this.idSelectorHandler = new LabelSourceStateIdSelectorHandler(dataSource, idService, selectedIds, assignment, lockedSegments);
	this.mergeDetachHandler = new LabelSourceStateMergeDetachHandler(dataSource, selectedIds, assignment, idService);
	this.commitHandler = new LabelSourceStateCommitHandler(this);
	if (dataSource instanceof MaskedSource<?, ?>)
	  this.shapeInterpolationMode = new ShapeInterpolationMode<>((MaskedSource<D, ?>)dataSource, this::refreshMeshes, selectedIds, idService, converter,
			  assignment);
	else
	  this.shapeInterpolationMode = null;
	this.streamSeedSetter = new ARGBStreamSeedSetter(converter.getStream());
	this.showOnlySelectedInStreamToggle = new ShowOnlySelectedInStreamToggle(converter.getStream());
	this.displayStatus = createDisplayStatus();

	// NOTE: this is needed to properly bind mesh info list and progress to the mesh manager.
	// The mesh generators are created after the mesh info list is initialized, so the initial binding doesn't do anything.
	Platform.runLater(this::refreshMeshes);
  }

  public LabelBlockLookup labelBlockLookup() {

	return this.labelBlockLookup;
  }

  public LabelSourceStatePaintHandler.BrushProperties getBrushProperties() {

	return paintHandler.getBrushProperties();
  }

  public MeshManagerWithAssignmentForSegments meshManager() {

	return this.meshManager;
  }

  public ManagedMeshSettings managedMeshSettings() {

	return this.meshManager.getManagedSettings();
  }

  public FragmentSegmentAssignmentState assignment() {

	return this.assignment;
  }

  public IdService idService() {

	return this.idService;
  }

  public SelectedIds selectedIds() {

	return this.selectedIds;
  }

  public LockedSegmentsState lockedSegments() {

	return this.lockedSegments;
  }

  public void refreshMeshes() {

	this.meshManager.refreshMeshes();
  }

  private HighlightingStreamConverter<T> highlightingStreamConverter() {

	return converter();
  }

  public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
  LabelSourceState<D, T> simpleSourceFromSingleRAI(
		  final RandomAccessibleInterval<D> data,
		  final double[] resolution,
		  final double[] offset,
		  final long maxId,
		  final String name,
		  final Group meshesGroup,
		  final ObjectProperty<ViewFrustum> viewFrustumProperty,
		  final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
		  final ExecutorService meshManagerExecutors,
		  final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> meshWorkersExecutors) {

	return simpleSourceFromSingleRAI(
			data,
			resolution,
			offset,
			new NoOpInvalidate<>(),
			maxId,
			name,
			meshesGroup,
			viewFrustumProperty,
			eyeToWorldTransformProperty,
			meshManagerExecutors,
			meshWorkersExecutors);
  }

  public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
  LabelSourceState<D, T> simpleSourceFromSingleRAI(
		  final RandomAccessibleInterval<D> data,
		  final double[] resolution,
		  final double[] offset,
		  final Invalidate<Long> invalidate,
		  final long maxId,
		  final String name,
		  final Group meshesGroup,
		  final ObjectProperty<ViewFrustum> viewFrustumProperty,
		  final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
		  final ExecutorService meshManagerExecutors,
		  final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> meshWorkersExecutors) {

	return simpleSourceFromSingleRAI(
			data,
			resolution,
			offset,
			invalidate,
			maxId,
			name,
			new LabelBlockLookupNoBlocks(),
			meshesGroup,
			viewFrustumProperty,
			eyeToWorldTransformProperty,
			meshManagerExecutors,
			meshWorkersExecutors);
  }

  public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
  LabelSourceState<D, T> simpleSourceFromSingleRAI(
		  final RandomAccessibleInterval<D> data,
		  final double[] resolution,
		  final double[] offset,
		  final long maxId,
		  final String name,
		  final LabelBlockLookup labelBlockLookup,
		  final Group meshesGroup,
		  final ObjectProperty<ViewFrustum> viewFrustumProperty,
		  final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
		  final ExecutorService meshManagerExecutors,
		  final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> meshWorkersExecutors) {

	return simpleSourceFromSingleRAI(
			data,
			resolution,
			offset,
			new NoOpInvalidate<>(),
			maxId,
			name,
			labelBlockLookup,
			meshesGroup,
			viewFrustumProperty,
			eyeToWorldTransformProperty,
			meshManagerExecutors,
			meshWorkersExecutors);
  }

  public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
  LabelSourceState<D, T> simpleSourceFromSingleRAI(
		  final RandomAccessibleInterval<D> data,
		  final double[] resolution,
		  final double[] offset,
		  final Invalidate<Long> invalidate,
		  final long maxId,
		  final String name,
		  final LabelBlockLookup labelBlockLookup,
		  final Group meshesGroup,
		  final ObjectProperty<ViewFrustum> viewFrustumProperty,
		  final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
		  final ExecutorService meshManagerExecutors,
		  final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> meshWorkersExecutors) {

	if (!Views.isZeroMin(data)) {
	  return simpleSourceFromSingleRAI(
			  Views.zeroMin(data),
			  resolution,
			  offset,
			  invalidate,
			  maxId,
			  name,
			  labelBlockLookup,
			  meshesGroup,
			  viewFrustumProperty,
			  eyeToWorldTransformProperty,
			  meshManagerExecutors,
			  meshWorkersExecutors
	  );
	}

	final AffineTransform3D mipmapTransform = new AffineTransform3D();
	mipmapTransform.set(
			resolution[0], 0, 0, offset[0],
			0, resolution[1], 0, offset[1],
			0, 0, resolution[2], offset[2]
	);

	final T vt = (T)VolatileTypeMatcher.getVolatileTypeForType(Util.getTypeFromInterval(data)).createVariable();
	vt.setValid(true);
	final RandomAccessibleInterval<T> vdata = Converters.convert(data, (s, t) -> t.get().set(s), vt);

	final RandomAccessibleIntervalDataSource<D, T> dataSource = new RandomAccessibleIntervalDataSource<>(
			data,
			vdata,
			mipmapTransform,
			invalidate,
			i -> new NearestNeighborInterpolatorFactory<>(),
			i -> new NearestNeighborInterpolatorFactory<>(),
			name);

	final SelectedIds selectedIds = new SelectedIds();
	final FragmentSegmentAssignmentOnlyLocal assignment = new FragmentSegmentAssignmentOnlyLocal(new FragmentSegmentAssignmentOnlyLocal.DoesNotPersist());
	final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);
	final LockedSegmentsOnlyLocal lockedSegments = new LockedSegmentsOnlyLocal(seg -> {
	});
	final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new
			ModalGoldenAngleSaturatedHighlightingARGBStream(
			selectedSegments,
			lockedSegments);

	final MeshManagerWithAssignmentForSegments meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
			dataSource,
			selectedSegments,
			stream,
			viewFrustumProperty,
			eyeToWorldTransformProperty,
			labelBlockLookup,
			meshManagerExecutors,
			meshWorkersExecutors);

	return new LabelSourceState<>(
			dataSource,
			new HighlightingStreamConverterIntegerType<>(stream),
			new ARGBCompositeAlphaYCbCr(),
			name,
			assignment,
			lockedSegments,
			new LocalIdService(maxId),
			selectedIds,
			meshManager,
			labelBlockLookup
	);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <D> LongFunction<Converter<D, BoolType>> equalsMaskForType(final D d) {

	if (d instanceof LabelMultisetType) {
	  return (LongFunction)equalMaskForLabelMultisetType();
	}

	if (d instanceof IntegerType<?>) {
	  return (LongFunction)equalMaskForIntegerType();
	}

	if (d instanceof RealType<?>) {
	  return (LongFunction)equalMaskForRealType();
	}

	return null;
  }

  private static LongFunction<Converter<LabelMultisetType, BoolType>> equalMaskForLabelMultisetType() {

	return id -> (s, t) -> t.set(s.contains(id));
  }

  private static <D extends IntegerType<D>> LongFunction<Converter<D, BoolType>> equalMaskForIntegerType() {

	return id -> (s, t) -> t.set(s.getIntegerLong() == id);
  }

  private static <D extends RealType<D>> LongFunction<Converter<D, BoolType>> equalMaskForRealType() {

	return id -> (s, t) -> t.set(s.getRealDouble() == id);
  }

  public static <D extends IntegerType<D>, T extends Volatile<D> & Type<T>> DataSource<BoolType, Volatile<BoolType>> labelToBooleanFragmentMaskSource(LabelSourceState<D, T> labelSource) {

	DataSource<D, T> underlyingSource = labelSource.getDataSource() instanceof MaskedSource<?, ?> ? (MaskedSource<D, T>)labelSource.getDataSource() : labelSource.getDataSource();
	FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments(new SelectedSegments(labelSource.selectedIds, labelSource.assignment));
	return new PredicateDataSource<>(underlyingSource, ConnectomicsLabelState.checkForType(underlyingSource.getDataType(), fragmentsInSelectedSegments), labelSource.nameProperty().get());
  }

  @Override
  public EventHandler<Event> stateSpecificGlobalEventHandler(final PainteraBaseView paintera, final KeyTracker keyTracker) {

	LOG.debug("Returning {}-specific global handler", getClass().getSimpleName());
	final NamedKeyCombination.CombinationMap keyBindings = paintera.getKeyAndMouseBindings().getConfigFor(this).getKeyCombinations();
	final DelegateEventHandlers.AnyHandler handler = DelegateEventHandlers.handleAny();
	handler.addEventHandler(
			KeyEvent.KEY_PRESSED,
			EventFX.KEY_PRESSED(
					LabelSourceStateKeys.REFRESH_MESHES,
					e -> {
					  e.consume();
					  LOG.debug("Key event triggered refresh meshes");
					  refreshMeshes();
					},
					keyBindings.get(LabelSourceStateKeys.REFRESH_MESHES)::matches));
	handler.addEventHandler(
			KeyEvent.KEY_PRESSED,
			EventFX.KEY_PRESSED(
					LabelSourceStateKeys.CANCEL_3D_FLOODFILL,
					e -> {
					  e.consume();
					  final FloodFillState state = floodFillState.get();
					  if (state != null && state.interrupt != null)
						state.interrupt.run();
					},
					e -> floodFillState.get() != null && keyBindings.get(LabelSourceStateKeys.CANCEL_3D_FLOODFILL).matches(e)));
	handler.addEventHandler(KeyEvent.KEY_PRESSED, EventFX.KEY_PRESSED(
			LabelSourceStateKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY,
			e -> {
			  e.consume();
			  this.showOnlySelectedInStreamToggle.toggleNonSelectionVisibility();
			},
			keyBindings.get(LabelSourceStateKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY)::matches));
	handler.addEventHandler(KeyEvent.KEY_PRESSED,
			streamSeedSetter.incrementHandler(keyBindings.get(LabelSourceStateKeys.ARGB_STREAM_INCREMENT_SEED)::getPrimaryCombination));
	handler.addEventHandler(KeyEvent.KEY_PRESSED,
			streamSeedSetter.decrementHandler(keyBindings.get(LabelSourceStateKeys.ARGB_STREAM_DECREMENT_SEED)::getPrimaryCombination));
	final DelegateEventHandlers.ListDelegateEventHandler<Event> listHandler = DelegateEventHandlers.listHandler();
	listHandler.addHandler(handler);
	listHandler.addHandler(commitHandler.globalHandler(paintera, paintera.getKeyAndMouseBindings().getConfigFor(this), keyTracker));
	return listHandler;
  }

  @Override
  public EventHandler<Event> stateSpecificViewerEventHandler(final PainteraBaseView paintera, final KeyTracker keyTracker) {

	LOG.debug("Returning {}-specific handler", getClass().getSimpleName());
	final DelegateEventHandlers.ListDelegateEventHandler<Event> handler = DelegateEventHandlers.listHandler();
	if (paintHandler != null)
	  handler.addHandler(paintHandler.viewerHandler(paintera, keyTracker));
	handler.addHandler(idSelectorHandler.viewerHandler(
			paintera,
			paintera.getKeyAndMouseBindings().getConfigFor(this),
			keyTracker,
			LabelSourceStateKeys.SELECT_ALL,
			LabelSourceStateKeys.SELECT_ALL_IN_CURRENT_VIEW,
			LabelSourceStateKeys.LOCK_SEGEMENT,
			LabelSourceStateKeys.NEXT_ID));
	handler.addHandler(mergeDetachHandler.viewerHandler(
			paintera,
			paintera.getKeyAndMouseBindings().getConfigFor(this),
			keyTracker,
			LabelSourceStateKeys.MERGE_ALL_SELECTED));
	return handler;
  }

  @Override
  public EventHandler<Event> stateSpecificViewerEventFilter(final PainteraBaseView paintera, final KeyTracker keyTracker) {

	LOG.debug("Returning {}-specific filter", getClass().getSimpleName());
	final DelegateEventHandlers.ListDelegateEventHandler<Event> filter = DelegateEventHandlers.listHandler();
	final KeyAndMouseBindings bindings = paintera.getKeyAndMouseBindings().getConfigFor(this);
	if (paintHandler != null)
	  filter.addHandler(paintHandler.viewerFilter(paintera, keyTracker));
	if (shapeInterpolationMode != null)
	  filter.addHandler(shapeInterpolationMode.modeHandler(
			  paintera,
			  keyTracker,
			  bindings,
			  BindingKeys.ENTER_SHAPE_INTERPOLATION_MODE,
			  BindingKeys.EXIT_SHAPE_INTERPOLATION_MODE,
			  BindingKeys.SHAPE_INTERPOLATION_APPLY_MASK,
			  BindingKeys.SHAPE_INTERPOLATION_EDIT_SELECTION_1,
			  BindingKeys.SHAPE_INTERPOLATION_EDIT_SELECTION_2));
	return filter;
  }

  @Override
  public void onAdd(final PainteraBaseView paintera) {

	highlightingStreamConverter().getStream().addListener(obs -> paintera.orthogonalViews().requestRepaint());
	selectedIds.addListener(obs -> paintera.orthogonalViews().requestRepaint());
	lockedSegments.addListener(obs -> paintera.orthogonalViews().requestRepaint());
	assignment.addListener(obs -> paintera.orthogonalViews().requestRepaint());
	paintera.viewer3D().meshesGroup().getChildren().add(meshManager.getMeshesGroup());
	assignment.addListener(o -> meshManager.setMeshesToSelection());
	meshManager.setMeshesToSelection();

	meshManager.getRendererSettings().getMeshesEnabledProperty().bind(paintera.viewer3D().meshesEnabledProperty());
	meshManager.getRendererSettings().getShowBlockBoundariesProperty().bind(paintera.viewer3D().showBlockBoundariesProperty());
	meshManager.getRendererSettings().getBlockSizeProperty().bind(paintera.viewer3D().rendererBlockSizeProperty());
	meshManager.getRendererSettings().getNumElementsPerFrameProperty().bind(paintera.viewer3D().numElementsPerFrameProperty());
	meshManager.getRendererSettings().getFrameDelayMsecProperty().bind(paintera.viewer3D().frameDelayMsecProperty());
	meshManager.getRendererSettings().getSceneUpdateDelayMsecProperty().bind(paintera.viewer3D().sceneUpdateDelayMsecProperty());
  }

  @Override
  public Node getDisplayStatus() {

	return displayStatus;
  }

  private HBox createDisplayStatus() {

	final Rectangle lastSelectedLabelColorRect = new Rectangle(13, 13);
	lastSelectedLabelColorRect.setStroke(Color.BLACK);

	final Tooltip lastSelectedLabelColorRectTooltip = new Tooltip();
	Tooltip.install(lastSelectedLabelColorRect, lastSelectedLabelColorRectTooltip);

	final InvalidationListener lastSelectedIdUpdater = obs -> {
	  InvokeOnJavaFXApplicationThread.invoke(() -> {
		if (selectedIds.isLastSelectionValid()) {
		  final long lastSelectedLabelId = selectedIds.getLastSelection();
		  final AbstractHighlightingARGBStream colorStream = highlightingStreamConverter().getStream();
		  if (colorStream != null) {
			final Color currSelectedColor = Colors.toColor(colorStream.argb(lastSelectedLabelId));
			lastSelectedLabelColorRect.setFill(currSelectedColor);
			lastSelectedLabelColorRect.setVisible(true);

			final StringBuilder activeIdText = new StringBuilder();
			final long segmentId = assignment.getSegment(lastSelectedLabelId);
			if (segmentId != lastSelectedLabelId)
			  activeIdText.append("Segment: " + segmentId).append(". ");
			activeIdText.append("Fragment: " + lastSelectedLabelId);
			lastSelectedLabelColorRectTooltip.setText(activeIdText.toString());
		  }
		} else {
		  lastSelectedLabelColorRect.setVisible(false);
		}
	  });
	};
	selectedIds.addListener(lastSelectedIdUpdater);
	assignment.addListener(lastSelectedIdUpdater);

	// add the same listener to the color stream (for example, the color should change when a new random seed value is set)
	final AbstractHighlightingARGBStream colorStream = highlightingStreamConverter().getStream();
	if (colorStream != null)
	  highlightingStreamConverter().getStream().addListener(lastSelectedIdUpdater);

	final ProgressIndicator paintingProgressIndicator = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
	paintingProgressIndicator.setPrefWidth(15);
	paintingProgressIndicator.setPrefHeight(15);
	paintingProgressIndicator.setMinWidth(Control.USE_PREF_SIZE);
	paintingProgressIndicator.setMinHeight(Control.USE_PREF_SIZE);
	paintingProgressIndicator.setVisible(false);

	final Tooltip paintingProgressIndicatorTooltip = new Tooltip();
	paintingProgressIndicator.setTooltip(paintingProgressIndicatorTooltip);

	final Runnable resetProgressIndicatorContextMenu = () -> {
	  final ContextMenu contextMenu = paintingProgressIndicator.contextMenuProperty().get();
	  if (contextMenu != null)
		contextMenu.hide();
	  paintingProgressIndicator.setContextMenu(null);
	  paintingProgressIndicator.setOnMouseClicked(null);
	  paintingProgressIndicator.setCursor(Cursor.DEFAULT);
	};

	final Consumer<ContextMenu> setProgressIndicatorContextMenu = contextMenu -> {
	  resetProgressIndicatorContextMenu.run();
	  paintingProgressIndicator.setContextMenu(contextMenu);
	  paintingProgressIndicator.setOnMouseClicked(event -> contextMenu.show(paintingProgressIndicator, event.getScreenX(), event.getScreenY()));
	  paintingProgressIndicator.setCursor(Cursor.HAND);
	};

	if (this.getDataSource() instanceof MaskedSource<?, ?>) {
	  final MaskedSource<?, ?> maskedSource = (MaskedSource<?, ?>)this.getDataSource();
	  maskedSource.isApplyingMaskProperty().addListener((obs, oldv, newv) -> {
		InvokeOnJavaFXApplicationThread.invoke(() -> {
		  paintingProgressIndicator.setVisible(newv);
		  if (newv) {
			final Mask<UnsignedLongType> currentMask = maskedSource.getCurrentMask();
			if (currentMask != null)
			  paintingProgressIndicatorTooltip.setText("Applying mask to canvas, label ID: " + currentMask.info.value.get());
		  }
		});
	  });
	}

	this.floodFillState.addListener((obs, oldv, newv) -> {
	  InvokeOnJavaFXApplicationThread.invoke(() -> {
		if (newv != null) {
		  paintingProgressIndicator.setVisible(true);
		  paintingProgressIndicatorTooltip.setText("Flood-filling, label ID: " + newv.labelId);

		  final MenuItem floodFillContextMenuCancelItem = new MenuItem("Cancel");
		  if (newv.interrupt != null) {
			floodFillContextMenuCancelItem.setOnAction(event -> newv.interrupt.run());
		  } else {
			floodFillContextMenuCancelItem.setDisable(true);
		  }
		  setProgressIndicatorContextMenu.accept(new ContextMenu(floodFillContextMenuCancelItem));
		} else {
		  paintingProgressIndicator.setVisible(false);
		  resetProgressIndicatorContextMenu.run();
		}
	  });
	});

	// only necessary if we actually have shape interpolation
	if (this.shapeInterpolationMode != null) {
	  final InvalidationListener shapeInterpolationModeStatusUpdater = obs -> {
		InvokeOnJavaFXApplicationThread.invoke(() -> {
		  final ModeState modeState = this.shapeInterpolationMode.modeStateProperty().get();
		  final ActiveSection activeSection = this.shapeInterpolationMode.activeSectionProperty().get();
		  if (modeState != null) {
			switch (modeState) {
			case Select:
			  statusTextProperty().set("Select #" + activeSection);
			  break;
			case Interpolate:
			  statusTextProperty().set("Interpolating");
			  break;
			case Preview:
			  statusTextProperty().set("Preview");
			  break;
			default:
			  statusTextProperty().set(null);
			  break;
			}
		  } else {
			statusTextProperty().set(null);
		  }
		  final boolean showProgressIndicator = modeState == ModeState.Interpolate;
		  paintingProgressIndicator.setVisible(showProgressIndicator);
		  paintingProgressIndicatorTooltip.setText(showProgressIndicator ? "Interpolating between sections..." : "");
		});
	  };

	  this.shapeInterpolationMode.modeStateProperty().addListener(shapeInterpolationModeStatusUpdater);
	  this.shapeInterpolationMode.activeSectionProperty().addListener(shapeInterpolationModeStatusUpdater);
	}

	final HBox displayStatus = new HBox(5, lastSelectedLabelColorRect, paintingProgressIndicator);
	displayStatus.setAlignment(Pos.CENTER_LEFT);
	displayStatus.setPadding(new Insets(0, 3, 0, 3));

	return displayStatus;
  }

  @Override
  public void onRemoval(final SourceInfo sourceInfo) {

	LOG.info("Removed LabelSourceState {}", nameProperty().get());
	meshManager.removeAllMeshes();
	LabelSourceStateCommitHandler.showCommitDialog(
			this,
			sourceInfo.indexOf(this.getDataSource()),
			false,
			(index, name) -> String.format("" +
					"Removing source %d: %s. " +
					"Uncommitted changes to the canvas and/or fragment-segment assignment will be lost if skipped.", index, name),
			false,
			"_Skip");
  }

  @Override
  public void onShutdown(final PainteraBaseView paintera) {

	LabelSourceStateCommitHandler.showCommitDialog(
			this,
			paintera.sourceInfo().indexOf(this.getDataSource()),
			false,
			(index, name) -> String.format("" +
					"Shutting down Paintera. " +
					"Uncommitted changes to the canvas will be lost for source %d: %s if skipped. " +
					"Uncommitted changes to the fragment-segment-assigment will be stored in the Paintera project (if any) " +
					"but can be committed to the data backend, as well.", index, name),
			false,
			"_Skip");
  }

  @Override
  public Node preferencePaneNode() {

	return new LabelSourceStatePreferencePaneNode(
			getDataSource(),
			compositeProperty(),
			converter(),
			meshManager,
			managedMeshSettings(),
			paintHandler == null ? null : paintHandler.getBrushProperties()).getNode();
  }

  @Override
  public KeyAndMouseBindings createKeyAndMouseBindings() {

	return new KeyAndMouseBindings(LabelSourceStateKeys.INSTANCE.namedCombinationsCopy());
  }

  @Override public DataSource<BoolType, Volatile<BoolType>> getIntersectableMask() {

	return labelToBooleanFragmentMaskSource(this);
  }

  private FragmentsInSelectedSegments getSelectedFragments() {

	final var selectedSegments = new SelectedSegments(selectedIds(), assignment());
	return new FragmentsInSelectedSegments(selectedSegments);
  }

  final ObjectBinding<FragmentLabelMeshCacheKey> meshCacheKeyBinding =
		  Bindings.createObjectBinding(
				  () -> new FragmentLabelMeshCacheKey(getSelectedFragments()),
				  selectedIds(),
				  assignment());

  @Override public ObjectBinding<FragmentLabelMeshCacheKey> getMeshCacheKeyBinding() {

	return meshCacheKeyBinding;
  }

  @Override public GetBlockListFor<FragmentLabelMeshCacheKey> getGetBlockListFor() {

	return this.meshManager().getGetBlockListForMeshCacheKey();
  }

}
