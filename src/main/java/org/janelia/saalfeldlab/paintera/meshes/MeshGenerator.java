package org.janelia.saalfeldlab.paintera.meshes;

import eu.mihosoft.jcsg.ext.openjfx.shape3d.PolygonMeshView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape3D;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.concurrent.PriorityExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
public class MeshGenerator<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final T id;

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks = FXCollections.observableHashMap();

	private final IntegerProperty preferredScaleLevel = new SimpleIntegerProperty(0);

	private final IntegerProperty highestScaleLevel = new SimpleIntegerProperty(0);

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty(0);

	private final BooleanProperty changed = new SimpleBooleanProperty(false);

	private final ObservableValue<Color> color;

	private final ObservableValue<Color> colorWithAlpha;

	private final ObjectProperty<ViewFrustum> viewFrustumProperty;

	private final ObjectProperty<AffineTransform3D> eyeToWorldTransform;

	private final Group root;

	private final Group meshesGroup;

	private final Group blocksGroup;

	private final IntegerProperty numTasks = new SimpleIntegerProperty(0);

	private final IntegerProperty numCompletedTasks = new SimpleIntegerProperty(0);

	private final MeshGeneratorJobManager<T> manager;

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty(0.5);

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty(5);

	private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);

	private final ObjectProperty<DrawMode> drawMode = new SimpleObjectProperty<>(DrawMode.FILL);

	private final ObjectProperty<CullFace> cullFace = new SimpleObjectProperty<>(CullFace.FRONT);

	private final DoubleProperty inflate = new SimpleDoubleProperty(1.0);

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	private BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> globalBlockTree;

	private CellGrid[] rendererGrids;

	public MeshGenerator(
			final DataSource<?, ?> source,
			final T segmentId,
			final InterruptibleFunction<T, Interval[]>[] blockListCache,
			final InterruptibleFunction<ShapeKey<T>, Triple<float[], float[], int[]>>[] meshCache,
			final MeshViewUpdateQueue<T> meshViewUpdateQueue,
			final ObservableIntegerValue color,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransform,
			final AffineTransform3D[] unshiftedWorldTransforms,
			final int preferredScaleLevel,
			final int highestScaleLevel,
			final int meshSimplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final int rendererBlockSize,
			final ExecutorService managers,
			final PriorityExecutorService<MeshWorkerPriority> workers,
			final ReadOnlyBooleanProperty showBlockBoundaries)
	{
		super();
		this.id = segmentId;
		this.color = Bindings.createObjectBinding(() -> fromInt(color.get()), color);
		this.viewFrustumProperty = viewFrustumProperty;
		this.eyeToWorldTransform = eyeToWorldTransform;

		this.colorWithAlpha = Bindings.createObjectBinding(
				() -> this.color.getValue().deriveColor(
						0,
						1.0,
						1.0,
						this.opacity.get()
				                                       ),
				this.color,
				this.opacity
		                                                  );

		this.changed.addListener((obs, oldv, newv) -> {if (newv) updateMeshes();});
		this.changed.addListener((obs, oldv, newv) -> changed.set(false));

		this.preferredScaleLevel.set(preferredScaleLevel);
		this.preferredScaleLevel.addListener((obs, oldv, newv) -> changed.set(true));

		this.highestScaleLevel.set(highestScaleLevel);
		this.highestScaleLevel.addListener((obs, oldv, newv) -> changed.set(true));

		this.meshSimplificationIterations.set(meshSimplificationIterations);
		this.meshSimplificationIterations.addListener((obs, oldv, newv) -> changed.set(true));

		this.smoothingLambda.set(smoothingLambda);
		this.smoothingLambda.addListener((obs, oldv, newv) -> changed.set(true));

		this.smoothingIterations.set(smoothingIterations);
		this.smoothingIterations.addListener((obs, oldv, newv) -> changed.set(true));

		this.meshesGroup = new Group();
		this.blocksGroup = new Group();
		this.root = new Group(meshesGroup, blocksGroup);

		this.root.visibleProperty().bind(this.isVisible);
		this.blocksGroup.visibleProperty().bind(showBlockBoundaries);

		this.manager = new MeshGeneratorJobManager<>(
				source,
				id,
				meshesAndBlocks,
				new ValuePair<>(meshesGroup, blocksGroup),
				meshViewUpdateQueue,
				blockListCache,
				meshCache,
				unshiftedWorldTransforms,
				managers,
				workers,
				numTasks,
				numCompletedTasks,
				rendererBlockSize
			);

		this.meshesAndBlocks.addListener((MapChangeListener<ShapeKey<T>, Pair<MeshView, Node>>) change ->
		{
			if (change.wasRemoved())
			{
				if (change.getValueRemoved().getA() != null)
				{
					final MeshView meshRemoved = change.getValueRemoved().getA();
					((PhongMaterial) meshRemoved.getMaterial()).diffuseColorProperty().unbind();
					meshRemoved.drawModeProperty().unbind();
					meshRemoved.cullFaceProperty().unbind();
					meshRemoved.scaleXProperty().unbind();
					meshRemoved.scaleYProperty().unbind();
					meshRemoved.scaleZProperty().unbind();
				}

				if (change.getValueRemoved().getB() != null)
				{
					final Node blockOutlineRemoved = change.getValueRemoved().getB();
					final Material material;
					if (blockOutlineRemoved instanceof PolygonMeshView)
						material = ((PolygonMeshView) blockOutlineRemoved).getMaterial();
					else if (blockOutlineRemoved instanceof Shape3D)
						material = ((Shape3D) blockOutlineRemoved).getMaterial();
					else
						material = null;
					if (material instanceof PhongMaterial)
						((PhongMaterial) material).diffuseColorProperty().unbind();
					blockOutlineRemoved.scaleXProperty().unbind();
					blockOutlineRemoved.scaleYProperty().unbind();
					blockOutlineRemoved.scaleZProperty().unbind();
				}
			}

			if (change.wasAdded())
			{
				if (change.getValueAdded().getA() != null)
				{
					final MeshView meshAdded = change.getValueAdded().getA();
					((PhongMaterial) meshAdded.getMaterial()).diffuseColorProperty().bind(this.colorWithAlpha);
					meshAdded.drawModeProperty().bind(this.drawMode);
					meshAdded.cullFaceProperty().bind(this.cullFace);
					meshAdded.scaleXProperty().bind(this.inflate);
					meshAdded.scaleYProperty().bind(this.inflate);
					meshAdded.scaleZProperty().bind(this.inflate);
				}

				if (change.getValueAdded().getB() != null)
				{
					final Node blockOutlineAdded = change.getValueAdded().getB();
					final Material material;
					if (blockOutlineAdded instanceof PolygonMeshView)
						material = ((PolygonMeshView) blockOutlineAdded).getMaterial();
					else if (blockOutlineAdded instanceof Shape3D)
						material = ((Shape3D) blockOutlineAdded).getMaterial();
					else
						material = null;
					if (material instanceof PhongMaterial)
						((PhongMaterial) material).diffuseColorProperty().bind(this.colorWithAlpha);
					blockOutlineAdded.scaleXProperty().bind(this.inflate);
					blockOutlineAdded.scaleYProperty().bind(this.inflate);
					blockOutlineAdded.scaleZProperty().bind(this.inflate);
					blockOutlineAdded.setDisable(true);
				}
			}
		});
	}

	public void update(
			final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> globalBlockTree,
			final CellGrid[] rendererGrids)
	{
		this.globalBlockTree = globalBlockTree;
		this.rendererGrids = rendererGrids;
		this.changed.set(true);
	}

	public synchronized void interrupt()
	{
		if (isInterrupted.get())
		{
			LOG.debug("MeshGenerator for {} has already been interrupted", id);
			return;
		}

		LOG.debug("Interrupting rendering tasks for {}", id);
		isInterrupted.set(true);
		manager.interrupt();
	}

	private void updateMeshes()
	{
		if (isInterrupted.get())
		{
			LOG.debug("MeshGenerator for {} has been interrupted, ignoring update request", id);
			return;
		}

		manager.submit(
				globalBlockTree,
				rendererGrids,
				meshSimplificationIterations.intValue(),
				smoothingLambda.doubleValue(),
				smoothingIterations.intValue()
			);
	}

	private static final Color fromInt(final int argb)
	{
		return Color.rgb(ARGBType.red(argb), ARGBType.green(argb), ARGBType.blue(argb), 1.0);
	}

	public T getId()
	{
		return id;
	}

	public Node getRoot()
	{
		return this.root;
	}

	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSimplificationIterations;
	}

	public IntegerProperty smoothingIterationsProperty()
	{
		return smoothingIterations;
	}

	public DoubleProperty smoothingLambdaProperty()
	{
		return smoothingLambda;
	}

	public IntegerProperty preferredScaleLevelProperty()
	{
		return this.preferredScaleLevel;
	}

	public IntegerProperty highestScaleLevelProperty()
	{
		return this.highestScaleLevel;
	}

	public ObservableIntegerValue numTasksProperty()
	{
		return this.numTasks;
	}

	public ObservableIntegerValue numCompletedTasksProperty()
	{
		return this.numCompletedTasks;
	}

	public DoubleProperty opacityProperty()
	{
		return this.opacity;
	}

	public ObjectProperty<DrawMode> drawModeProperty()
	{
		return this.drawMode;
	}

	public ObjectProperty<CullFace> cullFaceProperty()
	{
		return this.cullFace;
	}

	public DoubleProperty inflateProperty()
	{
		return this.inflate;
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	public void bindTo(final MeshSettings meshSettings)
	{
		LOG.debug("Binding to {}", meshSettings);
		opacityProperty().bind(meshSettings.opacityProperty());
		preferredScaleLevelProperty().bind(meshSettings.preferredScaleLevelProperty());
		highestScaleLevelProperty().bind(meshSettings.highestScaleLevelProperty());
		meshSimplificationIterationsProperty().bind(meshSettings.simplificationIterationsProperty());
		cullFaceProperty().bind(meshSettings.cullFaceProperty());
		drawModeProperty().bind(meshSettings.drawModeProperty());
		smoothingIterationsProperty().bind(meshSettings.smoothingIterationsProperty());
		smoothingLambdaProperty().bind(meshSettings.smoothingLambdaProperty());
		inflateProperty().bind(meshSettings.inflateProperty());
		isVisibleProperty().bind(meshSettings.isVisibleProperty());
	}

	public void unbind()
	{
		LOG.debug("Unbinding mesh generator");
		opacityProperty().unbind();
		preferredScaleLevelProperty().unbind();
		highestScaleLevelProperty().unbind();
		meshSimplificationIterationsProperty().unbind();
		cullFaceProperty().unbind();
		drawModeProperty().unbind();
		smoothingIterationsProperty().unbind();
		smoothingLambdaProperty().unbind();
		inflateProperty().unbind();
		isVisibleProperty().unbind();
	}
}
