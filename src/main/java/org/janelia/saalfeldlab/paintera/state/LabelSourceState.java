package org.janelia.saalfeldlab.paintera.state;

import bdv.util.volatiles.VolatileTypeMatcher;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Invalidate;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.actions.NamedKeyCombination;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.cache.NoOpInvalidate;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings;
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
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.LocalIdService;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.state.label.CommitHandler;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.FragmentLabelMeshCacheKey;
import org.janelia.saalfeldlab.paintera.stream.ARGBStreamSeedSetter;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterIntegerType;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.ShowOnlySelectedInStreamToggle;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
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

  private final LabelSourceStateIdSelectorHandler idSelectorHandler;

  private final LabelSourceStateMergeDetachHandler mergeDetachHandler;

  private final CommitHandler<SourceState<?, ?>> commitHandler;

  private final ObjectProperty<FloodFillState> floodFillState = new SimpleObjectProperty<>();

  private final ARGBStreamSeedSetter streamSeedSetter;

  private final ShowOnlySelectedInStreamToggle showOnlySelectedInStreamToggle;

  private final HBox displayStatus;
  private final AbstractHighlightingARGBStream stream;

  final ObjectBinding<FragmentLabelMeshCacheKey> meshCacheKeyBinding;

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
	this.idSelectorHandler = new LabelSourceStateIdSelectorHandler(dataSource, idService, selectedIds, assignment, lockedSegments, meshManager::refreshMeshes);
	this.mergeDetachHandler = new LabelSourceStateMergeDetachHandler(dataSource, selectedIds, assignment, idService);
	this.commitHandler = new CommitHandler<>(this, this::assignment);
	this.stream = converter.getStream();
	this.streamSeedSetter = new ARGBStreamSeedSetter(stream);
	this.showOnlySelectedInStreamToggle = new ShowOnlySelectedInStreamToggle(converter.getStream());
	this.displayStatus = ConnectomicsLabelState.createDisplayStatus(dataSource, this.floodFillState, selectedIds, assignment, this.stream);

	this.meshCacheKeyBinding = Bindings.createObjectBinding(() -> new FragmentLabelMeshCacheKey(getSelectedFragments()),
			selectedIds(),
			assignment());

	// NOTE: this is needed to properly bind mesh info list and progress to the mesh manager.
	// The mesh generators are created after the mesh info list is initialized, so the initial binding doesn't do anything.
	Platform.runLater(this::refreshMeshes);
  }

  public Converter<D, BoolType> getMaskForLabel(long label) {

	return this.maskForLabel.apply(label);
  }

  public long nextId() {

	return idSelectorHandler.nextId(false);
  }

  public long nextId(Boolean activate) {

	return idSelectorHandler.nextId(activate);
  }

  public void setFloodFillState(FloodFillState floodFillState) {

	this.floodFillState.setValue(floodFillState);
  }

  public LabelBlockLookup labelBlockLookup() {

	return this.labelBlockLookup;
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

	@SuppressWarnings("unchecked") final T vt = (T)VolatileTypeMatcher.getVolatileTypeForType(Util.getTypeFromInterval(data)).createVariable();
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

	return id -> (s, t) -> t.set(s.contains(id, new LabelMultisetEntry()));
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

  @Override public List<ActionSet> getViewerActionSets() {

	final var viewerActionsSets = new ArrayList<ActionSet>();
	NamedKeyCombination.CombinationMap keyCombinations = Paintera.getPaintera().getBaseView().getKeyAndMouseBindings().getConfigFor(this).getKeyCombinations();
	viewerActionsSets.addAll(idSelectorHandler.makeActionSets(keyCombinations, Paintera.getPaintera().getKeyTracker(), Paintera.getPaintera().getActiveViewer()::get));
	viewerActionsSets.addAll(mergeDetachHandler.makeActionSets(keyCombinations, Paintera.getPaintera().getActiveViewer()::get));
	return viewerActionsSets;
  }

  @Override public List<ActionSet> getGlobalActionSets() {

	final NamedKeyCombination.CombinationMap keyBindings = Paintera.getPaintera().getBaseView().getKeyAndMouseBindings().getConfigFor(this).getKeyCombinations();
	final var globalActionSets = new ArrayList<ActionSet>();
	var labelSourceStateGlobalActionSet = new ActionSet(LabelSourceStateKeys.CANCEL_3D_FLOODFILL, actionSet -> {
	  actionSet.addKeyAction(KeyEvent.KEY_PRESSED, action -> {
		action.keyMatchesBinding(keyBindings, LabelSourceStateKeys.REFRESH_MESHES);
		action.setConsume(true);
		action.onAction(event -> {
		  LOG.debug("Key event triggered refresh meshes");
		  refreshMeshes();
		});
	  });
	  actionSet.addKeyAction(KeyEvent.KEY_PRESSED, keyAction -> {
		keyAction.setConsume(true);
		keyAction.keyMatchesBinding(keyBindings, LabelSourceStateKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY);
		keyAction.onAction(keyEvent -> {
		  this.showOnlySelectedInStreamToggle.toggleNonSelectionVisibility();
		  Paintera.getPaintera().getBaseView().orthogonalViews().requestRepaint();
		});
	  });
	  actionSet.addKeyAction(KeyEvent.KEY_PRESSED, keyAction -> {
		keyAction.keyMatchesBinding(keyBindings, LabelSourceStateKeys.ARGB_STREAM_INCREMENT_SEED);
		keyAction.onAction(keyEvent -> streamSeedSetter.incrementStreamSeed());
	  });
	  actionSet.addKeyAction(KeyEvent.KEY_PRESSED, keyAction -> {
		keyAction.keyMatchesBinding(keyBindings, LabelSourceStateKeys.ARGB_STREAM_DECREMENT_SEED);
		keyAction.onAction(keyEvent -> streamSeedSetter.decrementStreamSeed());
	  });
	});
	globalActionSets.add(labelSourceStateGlobalActionSet);
	globalActionSets.add(commitHandler.makeActionSet$paintera(keyBindings, Paintera.getPaintera().getBaseView()));

	return globalActionSets;
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

  @Override
  public void onRemoval(final SourceInfo sourceInfo) {

	LOG.info("Removed LabelSourceState {}", nameProperty().get());
	meshManager.removeAllMeshes();
	CommitHandler.showCommitDialog(
			this,
			sourceInfo.indexOf(this.getDataSource()),
			false,
			(index, name) -> String.format("" +
					"Removing source %d: %s. " +
					"Uncommitted changes to the canvas and/or fragment-segment assignment will be lost if skipped.", index, name),
			false,
			"_Skip",
			this.assignment);
  }

  @Override
  public void onShutdown(final PainteraBaseView paintera) {

	CommitHandler.showCommitDialog(
			this,
			paintera.sourceInfo().indexOf(this.getDataSource()),
			false,
			(index, name) -> String.format("" +
					"Shutting down Paintera. " +
					"Uncommitted changes to the canvas will be lost for source %d: %s if skipped. " +
					"Uncommitted changes to the fragment-segment-assigment will be stored in the Paintera project (if any) " +
					"but can be committed to the data backend, as well.", index, name),
			false,
			"_Skip",
			this.assignment);
  }

  @Override
  public Node preferencePaneNode() {

	return new LabelSourceStatePreferencePaneNode(
			getDataSource(),
			compositeProperty(),
			converter(),
			meshManager,
			managedMeshSettings(),
			null).getNode();
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

  @Override public ObjectBinding<FragmentLabelMeshCacheKey> getMeshCacheKeyBinding() {

	return meshCacheKeyBinding;
  }

  @Override public GetBlockListFor<FragmentLabelMeshCacheKey> getGetBlockListFor() {

	return this.meshManager().getGetBlockListForMeshCacheKey();
  }

}
