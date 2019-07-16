package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import net.imglib2.Interval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;

/**
 * @author Philipp Hanslovsky
 */
public class MeshGenerator<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static int RETRIEVING_RELEVANT_BLOCKS = -1;

	public static int SUBMITTED_MESH_GENERATION_TASK = -2;

	private final DataSource<?, ?> source;

	private final T id;

	private final InterruptibleFunction<T, Interval[]>[] blockListCache;

	private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache;

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks = FXCollections.observableHashMap();

	private final IntegerProperty scaleIndex = new SimpleIntegerProperty(0);

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty(0);

	private final BooleanProperty changed = new SimpleBooleanProperty(false);

	private final ObservableValue<Color> color;

	private final ObservableValue<Color> colorWithAlpha;

	private final Group root;

	private final Group meshesGroup;

	private final Group blocksGroup;

	private final ViewFrustum viewFrustum;

	private final BooleanProperty isEnabled = new SimpleBooleanProperty(true);

	private final ExecutorService managers;

	private final ExecutorService workers;

	private final ObjectProperty<CompletableFuture<Void>> activeFuture = new SimpleObjectProperty<>();

	private final ObjectProperty<MeshGeneratorJobManager<T>.ManagementTask> activeTask = new SimpleObjectProperty<>();

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty(0);

	private final IntegerProperty completedTasks = new SimpleIntegerProperty(0);

	private final IntegerProperty successfulTasks = new SimpleIntegerProperty(0);

	private final MeshGeneratorJobManager<T> manager;

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty(0.5);

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty(5);

	private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);

	private final ObjectProperty<DrawMode> drawMode = new SimpleObjectProperty<>(DrawMode.FILL);

	private final ObjectProperty<CullFace> cullFace = new SimpleObjectProperty<>(CullFace.FRONT);

	private final DoubleProperty inflate = new SimpleDoubleProperty(1.0);

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	public MeshGenerator(
			final DataSource<?, ?> source,
			final ViewFrustum viewFrustum,
			final T segmentId,
			final InterruptibleFunction<T, Interval[]>[] blockListCache,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache,
			final ObservableIntegerValue color,
			final int scaleIndex,
			final int meshSimplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final ExecutorService managers,
			final ExecutorService workers)
	{
		super();
		this.source = source;
		this.id = segmentId;
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.color = Bindings.createObjectBinding(() -> fromInt(color.get()), color);
		this.managers = managers;
		this.workers = workers;

		this.manager = new MeshGeneratorJobManager<>(this.source, this.meshesAndBlocks, this.managers, this.workers);
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

		this.scaleIndex.set(scaleIndex);
		this.scaleIndex.addListener((obs, oldv, newv) -> changed.set(true));

		this.meshSimplificationIterations.set(meshSimplificationIterations);
		this.meshSimplificationIterations.addListener((obs, oldv, newv) -> changed.set(true));

		this.smoothingLambda.set(smoothingLambda);
		this.smoothingLambda.addListener((obs, oldv, newv) -> changed.set(true));

		this.smoothingIterations.set(smoothingIterations);
		this.smoothingIterations.addListener((obs, oldv, newv) -> changed.set(true));

		this.meshesGroup = new Group();
		this.blocksGroup = new Group();
		this.root = new Group(meshesGroup, blocksGroup);

		this.viewFrustum = viewFrustum;

		this.isEnabled.addListener((obs, oldv, newv) -> {
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				synchronized (this.meshesAndBlocks)
				{
					if (newv)
					{
						for (final Pair<MeshView, Node> meshAndBlock : meshesAndBlocks.values())
						{
							meshesGroup.getChildren().add(meshAndBlock.getA());
							blocksGroup.getChildren().add(meshAndBlock.getA());
						}
					}
					else
					{
						for (final Pair<MeshView, Node> meshAndBlock : meshesAndBlocks.values())
						{
							meshesGroup.getChildren().remove(meshAndBlock.getA());
							blocksGroup.getChildren().remove(meshAndBlock.getA());
						}
					}
				}
			});
		});

		this.meshesAndBlocks.addListener((MapChangeListener<ShapeKey<T>, Pair<MeshView, Node>>) change -> {
			if (change.wasRemoved())
			{
				final MeshView meshRemoved = change.getValueRemoved().getA();
				((PhongMaterial) meshRemoved.getMaterial()).diffuseColorProperty().unbind();
				meshRemoved.visibleProperty().unbind();
				meshRemoved.drawModeProperty().unbind();
				meshRemoved.cullFaceProperty().unbind();
				meshRemoved.scaleXProperty().unbind();
				meshRemoved.scaleYProperty().unbind();
				meshRemoved.scaleZProperty().unbind();
			}
			else
			{
				final MeshView meshAdded = change.getValueAdded().getA();
				((PhongMaterial) meshAdded.getMaterial()).diffuseColorProperty().bind(this
						.colorWithAlpha);
				meshAdded.visibleProperty().bind(this.isVisible);
				meshAdded.drawModeProperty().bind(this.drawMode);
				meshAdded.cullFaceProperty().bind(this.cullFace);
				meshAdded.scaleXProperty().bind(this.inflate);
				meshAdded.scaleYProperty().bind(this.inflate);
				meshAdded.scaleZProperty().bind(this.inflate);
			}

			if (change.wasRemoved())
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					meshesGroup.getChildren().remove(change.getValueRemoved().getA());
					blocksGroup.getChildren().remove(change.getValueRemoved().getB());
				});
			}
			else if (change.wasAdded())
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					if (this.isEnabled.get())
					{
						if (!meshesGroup.getChildren().contains(change.getValueAdded().getA()))
							meshesGroup.getChildren().add(change.getValueAdded().getA());

						if (!blocksGroup.getChildren().contains(change.getValueAdded().getB()))
							blocksGroup.getChildren().add(change.getValueAdded().getB());
					}
				});
			}
		});
	}

	public void update()
	{
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
		Optional.ofNullable(activeFuture.get()).ifPresent(future -> future.cancel(true));
		Optional.ofNullable(activeTask.get()).ifPresent(task -> task.interrupt());
		activeFuture.set(null);
		activeTask.set(null);
	}

	private synchronized void updateMeshes()
	{
		if (isInterrupted.get())
		{
			LOG.debug("MeshGenerator for {} has been interrupted, ignoring update request", id);
			return;
		}

		final Pair<MeshGeneratorJobManager<T>.ManagementTask, CompletableFuture<Void>> taskAndFuture = manager.submit(
				source,
				id,
				viewFrustum,
				scaleIndex.intValue(),
				meshSimplificationIterations.intValue(),
				smoothingLambda.doubleValue(),
				smoothingIterations.intValue(),
				blockListCache,
				meshCache,
				submittedTasks::set,
				completedTasks::set
			);

		this.activeTask.set(taskAndFuture.getA());
		this.activeFuture.set(taskAndFuture.getB());
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

	public BooleanProperty isEnabledProperty()
	{
		return this.isEnabled;
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

	public IntegerProperty scaleIndexProperty()
	{
		return this.scaleIndex;
	}

	public ObservableIntegerValue submittedTasksProperty()
	{
		return this.submittedTasks;
	}

	public ObservableIntegerValue completedTasksProperty()
	{
		return this.completedTasks;
	}

	public ObservableIntegerValue successfulTasksProperty()
	{
		return this.successfulTasks;
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
		scaleIndexProperty().bind(meshSettings.scaleLevelProperty());
		meshSimplificationIterationsProperty().bind(meshSettings.simplificationIterationsProperty());
		cullFaceProperty().bind(meshSettings.cullFaceProperty());
		drawModeProperty().bind(meshSettings.drawModeProperty());
		smoothingIterationsProperty().bind(meshSettings.smoothingIterationsProperty());
		smoothingLambdaProperty().bind(meshSettings.smoothingLambdaProperty());
		inflateProperty().bind(meshSettings.inflateProperty());
		isVisible.bind(meshSettings.isVisibleProperty());
	}

	public void unbind()
	{
		LOG.debug("Unbinding mesh generator");
		opacityProperty().unbind();
		scaleIndexProperty().unbind();
		meshSimplificationIterationsProperty().unbind();
		cullFaceProperty().unbind();
		drawModeProperty().unbind();
			smoothingIterationsProperty().unbind();
		smoothingLambdaProperty().unbind();
		inflateProperty().unbind();
		isVisible.unbind();
	}
}
