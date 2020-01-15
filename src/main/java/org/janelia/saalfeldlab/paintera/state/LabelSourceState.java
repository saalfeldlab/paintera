package org.janelia.saalfeldlab.paintera.state;

import bdv.util.volatiles.VolatileTypeMatcher;
import gnu.trove.set.hash.TLongHashSet;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.LocalIdService;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments;
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
public class LabelSourceState<D extends IntegerType<D>, T>
		extends
		MinimalSourceState<D, T, DataSource<D, T>, HighlightingStreamConverter<T>>
		implements
		HasMeshCache<TLongHashSet>,
		HasSelectedIds,
		HasFragmentSegmentAssignments,
		HasFloodFillState
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final LongFunction<Converter<D, BoolType>> maskForLabel;

	private final FragmentSegmentAssignmentState assignment;

	private final SelectedIds selectedIds;

	private final IdService idService;

	private final MeshManagerWithAssignmentForSegments meshManager;

	private final LockedSegmentsState lockedSegments;

	private final LabelBlockLookup labelBlockLookup;

	private final LabelSourceStatePaintHandler paintHandler;

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
			final LabelBlockLookup labelBlockLookup)
	{
		super(dataSource, converter, composite, name);
		LOG.warn("Using deprectaed class LabelSourceState. Use ConnectomicsLabelState instead.");
		final D d = dataSource.getDataType();
		this.maskForLabel = equalsMaskForType(d);
		this.assignment = assignment;
		this.lockedSegments = lockedSegments;
		this.selectedIds = selectedIds;
		this.idService = idService;
		this.meshManager = meshManager;
		this.labelBlockLookup = labelBlockLookup;
		this.paintHandler = new LabelSourceStatePaintHandler(selectedIds, (LongFunction) maskForLabel);
		this.idSelectorHandler = new LabelSourceStateIdSelectorHandler(dataSource, idService, selectedIds, assignment, lockedSegments);
		this.mergeDetachHandler = new LabelSourceStateMergeDetachHandler(dataSource, selectedIds, assignment, idService);
		this.commitHandler = new LabelSourceStateCommitHandler(this);
		if (dataSource instanceof MaskedSource<?, ?>)
			this.shapeInterpolationMode = new ShapeInterpolationMode<>((MaskedSource<D, ?>) dataSource, this::refreshMeshes, selectedIds, idService, converter, assignment);
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

	public MeshManagerWithAssignmentForSegments meshManager()
	{
		return this.meshManager;
	}

	public ManagedMeshSettings managedMeshSettings()
	{
		return null;
//		return this.meshManager.managedMeshSettings();
	}

	@Override
	public FragmentSegmentAssignmentState assignment()
	{
		return this.assignment;
	}

	public IdService idService()
	{
		return this.idService;
	}

	@Override
	public SelectedIds selectedIds()
	{
		return this.selectedIds;
	}

	@Override
	public void invalidateAll()
	{
		// TODO
//		this.meshManager.invalidateCaches();
	}

	public LockedSegmentsState lockedSegments()
	{
		return this.lockedSegments;
	}

	@Override
	public ObjectProperty<FloodFillState> floodFillState()
	{
		return this.floodFillState;
	}

//	@Override
	public void refreshMeshes()
	{
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
			final AxisOrder axisOrder,
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
				axisOrder,
				maxId,
				name,
				meshesGroup,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				meshManagerExecutors,
				meshWorkersExecutors
			);
	}

	public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
	LabelSourceState<D, T> simpleSourceFromSingleRAI(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final Invalidate<Long> invalidate,
			final AxisOrder axisOrder,
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
				axisOrder,
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
			final AxisOrder axisOrder,
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
				axisOrder,
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

	public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
	LabelSourceState<D, T> simpleSourceFromSingleRAI(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final Invalidate<Long> invalidate,
			final AxisOrder axisOrder,
			final long maxId,
			final String name,
			final LabelBlockLookup labelBlockLookup,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ExecutorService meshManagerExecutors,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> meshWorkersExecutors) {

		if (!Views.isZeroMin(data))
		{
			return simpleSourceFromSingleRAI(
					Views.zeroMin(data),
					resolution,
					offset,
					invalidate,
					axisOrder,
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

		final T vt = (T) VolatileTypeMatcher.getVolatileTypeForType(Util.getTypeFromInterval(data)).createVariable();
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

		final SelectedIds                        selectedIds    = new SelectedIds();
		final FragmentSegmentAssignmentOnlyLocal assignment     = new FragmentSegmentAssignmentOnlyLocal(new FragmentSegmentAssignmentOnlyLocal.DoesNotPersist());
		final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);
		final LockedSegmentsOnlyLocal            lockedSegments = new LockedSegmentsOnlyLocal(seg -> {});
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
	private static <D> LongFunction<Converter<D, BoolType>> equalsMaskForType(final D d)
	{
		if (d instanceof LabelMultisetType) { return (LongFunction) equalMaskForLabelMultisetType(); }

		if (d instanceof IntegerType<?>) { return (LongFunction) equalMaskForIntegerType(); }

		if (d instanceof RealType<?>) { return (LongFunction) equalMaskForRealType(); }

		return null;
	}

	private static LongFunction<Converter<LabelMultisetType, BoolType>> equalMaskForLabelMultisetType()
	{
		return id -> (s, t) -> t.set(s.contains(id));
	}

	private static <D extends IntegerType<D>> LongFunction<Converter<D, BoolType>> equalMaskForIntegerType()
	{
		return id -> (s, t) -> t.set(s.getIntegerLong() == id);
	}

	private static <D extends RealType<D>> LongFunction<Converter<D, BoolType>> equalMaskForRealType()
	{
		return id -> (s, t) -> t.set(s.getRealDouble() == id);
	}

	@Override
	public EventHandler<Event> stateSpecificGlobalEventHandler(final PainteraBaseView paintera, final KeyTracker keyTracker) {
		LOG.debug("Returning {}-specific global handler", getClass().getSimpleName());
		final NamedKeyCombination.CombinationMap keyBindings = paintera.getKeyAndMouseBindings().getConfigFor(this).getKeyCombinations();
		final DelegateEventHandlers.AnyHandler handler = DelegateEventHandlers.handleAny();
		handler.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						BindingKeys.REFRESH_MESHES,
						e -> {
							e.consume();
							LOG.debug("Key event triggered refresh meshes");
							refreshMeshes();
						},
						keyBindings.get(BindingKeys.REFRESH_MESHES)::matches));
		handler.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						BindingKeys.CANCEL_3D_FLOODFILL,
						e -> {
							e.consume();
							final FloodFillState state = floodFillState.get();
							if (state != null && state.interrupt != null)
								state.interrupt.run();
						},
						e -> floodFillState.get() != null && keyBindings.get(BindingKeys.CANCEL_3D_FLOODFILL).matches(e)));
		handler.addEventHandler(KeyEvent.KEY_PRESSED, EventFX.KEY_PRESSED(
				BindingKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY,
				e -> { e.consume(); this.showOnlySelectedInStreamToggle.toggleNonSelectionVisibility(); },
				keyBindings.get(BindingKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY)::matches));
		handler.addEventHandler(KeyEvent.KEY_PRESSED, streamSeedSetter.incrementHandler(keyBindings.get(BindingKeys.ARGB_STREAM_INCREMENT_SEED)::getPrimaryCombination));
		handler.addEventHandler(KeyEvent.KEY_PRESSED, streamSeedSetter.decrementHandler(keyBindings.get(BindingKeys.ARGB_STREAM_DECREMENT_SEED)::getPrimaryCombination));
		final DelegateEventHandlers.ListDelegateEventHandler<Event> listHandler = DelegateEventHandlers.listHandler();
		listHandler.addHandler(handler);
		listHandler.addHandler(commitHandler.globalHandler(paintera, paintera.getKeyAndMouseBindings().getConfigFor(this), keyTracker));
		return listHandler;
	}

//	@Override
//	public EventHandler<Event> stateSpecificGlobalEventFilter(PainteraBaseView paintera, KeyTracker keyTracker) {
//		return e -> {
//			LOG.debug("Default state specific event filter: Not handling anything");
//		};
//	}

	@Override
	public EventHandler<Event> stateSpecificViewerEventHandler(final PainteraBaseView paintera, final KeyTracker keyTracker) {
		LOG.debug("Returning {}-specific handler", getClass().getSimpleName());
		final DelegateEventHandlers.ListDelegateEventHandler<Event> handler = DelegateEventHandlers.listHandler();
		handler.addHandler(paintHandler.viewerHandler(paintera, keyTracker));
		handler.addHandler(idSelectorHandler.viewerHandler(
				paintera,
				paintera.getKeyAndMouseBindings().getConfigFor(this),
				keyTracker,
				BindingKeys.SELECT_ALL,
				BindingKeys.SELECT_ALL_IN_CURRENT_VIEW,
				BindingKeys.LOCK_SEGEMENT,
				BindingKeys.NEXT_ID));
		handler.addHandler(mergeDetachHandler.viewerHandler(
				paintera,
				paintera.getKeyAndMouseBindings().getConfigFor(this),
				keyTracker,
				BindingKeys.MERGE_ALL_SELECTED));
		return handler;
	}

	@Override
	public EventHandler<Event> stateSpecificViewerEventFilter(final PainteraBaseView paintera, final KeyTracker keyTracker) {
		LOG.debug("Returning {}-specific filter", getClass().getSimpleName());
		final DelegateEventHandlers.ListDelegateEventHandler<Event> filter = DelegateEventHandlers.listHandler();
		final KeyAndMouseBindings bindings = paintera.getKeyAndMouseBindings().getConfigFor(this);
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

//		TODO
//		meshManager().areMeshesEnabledProperty().bind(paintera.viewer3D().isMeshesEnabledProperty());
//		meshManager().showBlockBoundariesProperty().bind(paintera.viewer3D().showBlockBoundariesProperty());
//		meshManager().rendererBlockSizeProperty().bind(paintera.viewer3D().rendererBlockSizeProperty());
//		meshManager().numElementsPerFrameProperty().bind(paintera.viewer3D().numElementsPerFrameProperty());
//		meshManager().frameDelayMsecProperty().bind(paintera.viewer3D().frameDelayMsecProperty());
//		meshManager().sceneUpdateDelayMsecProperty().bind(paintera.viewer3D().sceneUpdateDelayMsecProperty());
	}

	@Override
	public Node getDisplayStatus()
	{
		return displayStatus;
	}

	private HBox createDisplayStatus()
	{
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

		if (this.getDataSource() instanceof MaskedSource<?, ?>)
		{
			final MaskedSource<?, ?> maskedSource = (MaskedSource<?, ?>) this.getDataSource();
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
		if (this.shapeInterpolationMode != null ) {
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
	public void onRemoval(SourceInfo sourceInfo) {
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
	public void onShutdown(PainteraBaseView paintera) {
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
				paintHandler.getBrushProperties()).getNode();
	}

	@Override
	public KeyAndMouseBindings createKeyAndMouseBindings() {
		final KeyAndMouseBindings bindings = new KeyAndMouseBindings();
		try {
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.SELECT_ALL, new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.SELECT_ALL_IN_CURRENT_VIEW, new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.LOCK_SEGEMENT, new KeyCodeCombination(KeyCode.L)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.NEXT_ID, new KeyCodeCombination(KeyCode.N)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.COMMIT_DIALOG, new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.MERGE_ALL_SELECTED, new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.ENTER_SHAPE_INTERPOLATION_MODE, new KeyCodeCombination(KeyCode.S)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.EXIT_SHAPE_INTERPOLATION_MODE, new KeyCodeCombination(KeyCode.ESCAPE)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.SHAPE_INTERPOLATION_APPLY_MASK, new KeyCodeCombination(KeyCode.ENTER)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.SHAPE_INTERPOLATION_EDIT_SELECTION_1, new KeyCodeCombination(KeyCode.DIGIT1)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.SHAPE_INTERPOLATION_EDIT_SELECTION_2, new KeyCodeCombination(KeyCode.DIGIT2)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.ARGB_STREAM_INCREMENT_SEED, new KeyCodeCombination(KeyCode.C)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.ARGB_STREAM_DECREMENT_SEED, new KeyCodeCombination(KeyCode.C, KeyCombination.SHIFT_DOWN)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.REFRESH_MESHES, new KeyCodeCombination(KeyCode.R)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.CANCEL_3D_FLOODFILL, new KeyCodeCombination(KeyCode.ESCAPE)));
			bindings.getKeyCombinations().addCombination(new NamedKeyCombination(BindingKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY, new KeyCodeCombination(KeyCode.V, KeyCombination.SHIFT_DOWN)));

		} catch (NamedKeyCombination.CombinationMap.KeyCombinationAlreadyInserted keyCombinationAlreadyInserted) {
			keyCombinationAlreadyInserted.printStackTrace();
			// TODO probably not necessary to check for exceptions here, but maybe throw runtime exception?
		}
		return bindings;
	}

	public static final class BindingKeys {

		public static final String SELECT_ALL = "select all";

		public static final String SELECT_ALL_IN_CURRENT_VIEW = "select all in current view";

		public static final String LOCK_SEGEMENT = "lock segment";

		public static final String NEXT_ID = "next id";

		public static final String COMMIT_DIALOG = "commit dialog";

		public static final String MERGE_ALL_SELECTED = "merge all selected";

		public static final String ENTER_SHAPE_INTERPOLATION_MODE = "shape interpolation: enter mode";

		public static final String EXIT_SHAPE_INTERPOLATION_MODE = "shape interpolation: exit mode";

		public static final String SHAPE_INTERPOLATION_APPLY_MASK = "shape interpolation: apply mask";

		public static final String SHAPE_INTERPOLATION_EDIT_SELECTION_1 = "shape interpolation: edit selection 1";

		public static final String SHAPE_INTERPOLATION_EDIT_SELECTION_2 = "shape interpolation: edit selection 2";

		public static final String ARGB_STREAM_INCREMENT_SEED = "argb stream: increment seed";

		public static final String ARGB_STREAM_DECREMENT_SEED = "argb stream: decrement seed";

		public static final String REFRESH_MESHES = "refresh meshes";

		public static final String CANCEL_3D_FLOODFILL = "3d floodfill: cancel";

		public static final String TOGGLE_NON_SELECTED_LABELS_VISIBILITY = "toggle non-selected labels visibility";

	}
}
