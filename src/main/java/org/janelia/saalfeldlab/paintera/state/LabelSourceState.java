package org.janelia.saalfeldlab.paintera.state;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.cache.InvalidateAll;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.Composite;
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
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterIntegerType;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.concurrent.PriorityExecutorService;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pivovarit.function.ThrowingFunction;

import bdv.util.volatiles.VolatileTypeMatcher;
import gnu.trove.set.hash.TLongHashSet;
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
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class LabelSourceState<D extends IntegerType<D>, T>
		extends
		MinimalSourceState<D, T, DataSource<D, T>, HighlightingStreamConverter<T>>
		implements
		HasMeshes<TLongHashSet>,
		HasMeshCache<TLongHashSet>,
		HasIdService,
		HasSelectedIds,
		HasHighlightingStreamConverter<T>,
		HasMaskForLabel<D>,
		HasFragmentSegmentAssignments,
		HasLockedSegments,
		HasFloodFillState
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final LongFunction<Converter<D, BoolType>> maskForLabel;


	private final FragmentSegmentAssignmentState assignment;

	private final SelectedIds selectedIds;

	private final IdService idService;

	private final MeshManager<Long, TLongHashSet> meshManager;

	private final LockedSegmentsState lockedSegments;

	private final LabelBlockLookup labelBlockLookup;

	private final LabelSourceStatePaintHandler paintHandler;

	private final LabelSourceStateIdSelectorHandler idSelectorHandler;

	private final LabelSourceStateMergeDetachHandler mergeDetachHandler;

	private final ShapeInterpolationMode<D> shapeInterpolationMode;

	private final ObjectProperty<FloodFillState> floodFillState = new SimpleObjectProperty<>();

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
			final MeshManager<Long, TLongHashSet> meshManager,
			final LabelBlockLookup labelBlockLookup)
	{
		super(dataSource, converter, composite, name);
		final D d = dataSource.getDataType();
		this.maskForLabel = equalsMaskForType(d);
		this.assignment = assignment;
		this.lockedSegments = lockedSegments;
		this.selectedIds = selectedIds;
		this.idService = idService;
		this.meshManager = meshManager;
		this.labelBlockLookup = labelBlockLookup;
		this.paintHandler = new LabelSourceStatePaintHandler(selectedIds);
		this.idSelectorHandler = new LabelSourceStateIdSelectorHandler(dataSource, selectedIds, assignment, lockedSegments);
		this.mergeDetachHandler = new LabelSourceStateMergeDetachHandler(dataSource, selectedIds, assignment, idService);
		if (dataSource instanceof MaskedSource<?, ?>)
			this.shapeInterpolationMode = new ShapeInterpolationMode<>((MaskedSource<D, ?>) dataSource, this, selectedIds, idService, converter, assignment);
		else
			this.shapeInterpolationMode = null;
		this.displayStatus = createDisplayStatus();
		assignment.addListener(obs -> stain());
		selectedIds.addListener(obs -> stain());
		lockedSegments.addListener(obs -> stain());
	}

	public LabelBlockLookup labelBlockLookup() {
		return this.labelBlockLookup;
	}

	@Override
	public LongFunction<Converter<D, BoolType>> maskForLabel()
	{
		return this.maskForLabel;
	}

	@Override
	public MeshManager<Long, TLongHashSet> meshManager()
	{
		return this.meshManager;
	}

	@Override
	public ManagedMeshSettings managedMeshSettings()
	{
		return this.meshManager.managedMeshSettings();
	}

	@Override
	public FragmentSegmentAssignmentState assignment()
	{
		return this.assignment;
	}

	@Override
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
		invalidateAllMeshCaches();
		invalidateAllBlockCaches();
	}

	public void invalidateAllMeshCaches()
	{
		this.meshManager.invalidateMeshCaches();
	}

	@Override
	public LockedSegmentsState lockedSegments()
	{
		return this.lockedSegments;
	}

	@Override
	public ObjectProperty<FloodFillState> floodFillState()
	{
		return this.floodFillState;
	}


	public void invalidateAllBlockCaches()
	{
//		this.clearBlockCaches.run();
	}

	@Override
	public void refreshMeshes()
	{
		this.invalidateAll();
		final long[] selection     = this.selectedIds.getActiveIds();
		final long   lastSelection = this.selectedIds.getLastSelection();
		this.selectedIds.deactivateAll();
		this.selectedIds.activate(selection);
		this.selectedIds.activateAlso(lastSelection);
	}

	@Override
	public HighlightingStreamConverter<T> highlightingStreamConverter() {
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
			final GlobalCache globalCache,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ExecutorService meshManagerExecutors,
			final PriorityExecutorService<MeshWorkerPriority> meshWorkersExecutors) {

		return simpleSourceFromSingleRAI(
				data,
				resolution,
				offset,
				() -> {},
				axisOrder,
				maxId,
				name,
				globalCache,
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
			final InvalidateAll invalidateAll,
			final AxisOrder axisOrder,
			final long maxId,
			final String name,
			final GlobalCache globalCache,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ExecutorService meshManagerExecutors,
			final PriorityExecutorService<MeshWorkerPriority> meshWorkersExecutors) {

		final int[] blockSize;
		if (data instanceof AbstractCellImg<?, ?, ?, ?>)
		{
			final CellGrid grid = ((AbstractCellImg<?, ?, ?, ?>) data).getCellGrid();
			blockSize = new int[grid.numDimensions()];
			Arrays.setAll(blockSize, grid::cellDimension);
		}
		else
		{
			blockSize = new int[] {64, 64, 64};
		}

		final Interval[] intervals = Grids.collectAllContainedIntervals(
				Intervals.dimensionsAsLongArray(data),
				blockSize
		                                                               )
				.stream()
				.toArray(Interval[]::new);

		@SuppressWarnings("unchecked") final InterruptibleFunction<Long, Interval[]>[] backgroundBlockCaches = new
				InterruptibleFunction[] {
				InterruptibleFunction.fromFunction(id -> intervals)
		};
		final LabelBlockLookup labelBlockLookup = new LabelBlockLookupNoBlocks();
		return simpleSourceFromSingleRAI(
				data,
				resolution,
				offset,
				invalidateAll,
				axisOrder,
				maxId,
				name,
				labelBlockLookup,
				globalCache,
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
			final GlobalCache globalCache,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ExecutorService meshManagerExecutors,
			final PriorityExecutorService<MeshWorkerPriority> meshWorkersExecutors) {

		return simpleSourceFromSingleRAI(
				data,
				resolution,
				offset,
				() -> {},
				axisOrder,
				maxId,
				name,
				labelBlockLookup,
				globalCache,
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
			final InvalidateAll invalidateAll,
			final AxisOrder axisOrder,
			final long maxId,
			final String name,
			final LabelBlockLookup labelBlockLookup,
			final GlobalCache globalCache,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ExecutorService meshManagerExecutors,
			final PriorityExecutorService<MeshWorkerPriority> meshWorkersExecutors) {

		if (!Views.isZeroMin(data))
		{
			return simpleSourceFromSingleRAI(
					Views.zeroMin(data),
					resolution,
					offset,
					invalidateAll,
					axisOrder,
					maxId,
					name,
					labelBlockLookup,
					globalCache,
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
				invalidateAll,
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				name
		);

		final SelectedIds                        selectedIds    = new SelectedIds();
		final FragmentSegmentAssignmentOnlyLocal assignment     = new FragmentSegmentAssignmentOnlyLocal(new FragmentSegmentAssignmentOnlyLocal.DoesNotPersist());
		final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);
		final LockedSegmentsOnlyLocal            lockedSegments = new LockedSegmentsOnlyLocal(seg -> {});
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new
				ModalGoldenAngleSaturatedHighlightingARGBStream(
						selectedSegments,
						lockedSegments
		);


		final ToLongFunction<T> toLong = integer -> {
			final long val = integer.get().getIntegerLong();
			return val;
		};

		final Function<Long, Interval[]> f = ThrowingFunction.unchecked(id -> labelBlockLookup.read(0, id));
		final InterruptibleFunction<Long, Interval[]>[] backgroundBlockCaches = InterruptibleFunction.fromFunction(new Function[]{f});

		final MeshManagerWithAssignmentForSegments meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
				dataSource,
				selectedSegments,
				stream,
				meshesGroup,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				backgroundBlockCaches,
				globalCache::createNewCache,
				meshManagerExecutors,
				meshWorkersExecutors
			);

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
		final DelegateEventHandlers.AnyHandler handler = DelegateEventHandlers.handleAny();
		handler.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"refresh meshes",
						e -> {
							e.consume();
							LOG.debug("Key event triggered refresh meshes");
							refreshMeshes();
						},
						e -> keyTracker.areOnlyTheseKeysDown(KeyCode.R)
			));
		handler.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"cancel 3d floodfill",
						e -> {
							e.consume();
							final FloodFillState state = floodFillState.get();
							if (state != null && state.interrupt != null)
								state.interrupt.run();
						},
						e -> floodFillState.get() != null && keyTracker.areOnlyTheseKeysDown(KeyCode.ESCAPE)
			));
		return handler;
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
		handler.addHandler(idSelectorHandler.viewerHandler(paintera, keyTracker));
		handler.addHandler(mergeDetachHandler.viewerHandler(paintera, keyTracker));
		return handler;
	}

	@Override
	public EventHandler<Event> stateSpecificViewerEventFilter(final PainteraBaseView paintera, final KeyTracker keyTracker) {
		LOG.debug("Returning {}-specific filter", getClass().getSimpleName());
		final DelegateEventHandlers.ListDelegateEventHandler<Event> filter = DelegateEventHandlers.listHandler();
		filter.addHandler(paintHandler.viewerFilter(paintera, keyTracker));
		if (shapeInterpolationMode != null)
			filter.addHandler(shapeInterpolationMode.modeHandler(paintera, keyTracker));
		return filter;
	}

	@Override
	public void onAdd(final PainteraBaseView paintera) {
		highlightingStreamConverter().getStream().addListener(obs -> paintera.orthogonalViews().requestRepaint());
		selectedIds.addListener(obs -> paintera.orthogonalViews().requestRepaint());
		lockedSegments.addListener(obs -> paintera.orthogonalViews().requestRepaint());
		meshManager().areMeshesEnabledProperty().bind(paintera.viewer3D().isMeshesEnabledProperty());
		meshManager().showBlockBoundariesProperty().bind(paintera.viewer3D().showBlockBoundariesProperty());
		meshManager().rendererBlockSizeProperty().bind(paintera.viewer3D().rendererBlockSizeProperty());
		meshManager().numElementsPerFrameProperty().bind(paintera.viewer3D().numElementsPerFrameProperty());
		meshManager().frameDelayMsecProperty().bind(paintera.viewer3D().frameDelayMsecProperty());
		meshManager().sceneUpdateDelayMsecProperty().bind(paintera.viewer3D().sceneUpdateDelayMsecProperty());
		assignment.addListener(obs -> paintera.orthogonalViews().requestRepaint());
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

		final HBox displayStatus = new HBox(5,
				lastSelectedLabelColorRect,
				paintingProgressIndicator
			);
		displayStatus.setAlignment(Pos.CENTER_LEFT);
		displayStatus.setPadding(new Insets(0, 3, 0, 3));

		return displayStatus;
	}
}
