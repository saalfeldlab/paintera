package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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
import javafx.collections.ObservableList;
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
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.meshes.MeshGeneratorJobManager.ManagementTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Philipp Hanslovsky
 */
public class MeshGenerator<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static int RETRIEVING_RELEVANT_BLOCKS = -1;

	public static int SUBMITTED_MESH_GENERATION_TASK = -2;

	public static class BlockListKey
	{

		private final long id;

		private final int scaleIndex;

		public BlockListKey(final long id, final int scaleIndex)
		{
			super();
			this.id = id;
			this.scaleIndex = scaleIndex;
		}

		public long id()
		{
			return this.id;
		}

		public int scaleIndex()
		{
			return this.scaleIndex;
		}

		@Override
		public int hashCode()
		{
			int result = scaleIndex;
			result = 31 * result + (int) (id ^ id >> 32);
			return result;
		}

		@Override
		public boolean equals(final Object other)
		{
			if (other instanceof BlockListKey)
			{
				final BlockListKey otherKey = (BlockListKey) other;
				return id == otherKey.id && scaleIndex == otherKey.scaleIndex;
			}
			return false;
		}

	}

	private final T id;

	private final InterruptibleFunction<T, Interval[]>[] blockListCache;

	private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache;

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

	private final ObservableMap<ShapeKey<T>, MeshView> meshes = FXCollections.observableHashMap();

	private final IntegerProperty scaleIndex = new SimpleIntegerProperty(0);

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty(0);

	private final BooleanProperty changed = new SimpleBooleanProperty(false);

	private final ObservableValue<Color> color;

	private final ObservableValue<Color> colorWithAlpha;

	private final Group root;

	private final BooleanProperty isEnabled = new SimpleBooleanProperty(true);

	private final ExecutorService managers;

	private final ExecutorService workers;

	private final ObjectProperty<Future<Void>> activeFuture = new SimpleObjectProperty<>();

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

	//
	public MeshGenerator(
			final Group root,
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
		this.id = segmentId;
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.color = Bindings.createObjectBinding(() -> fromInt(color.get()), color);
		this.managers = managers;
		this.workers = workers;
		this.manager = new MeshGeneratorJobManager<>(this.meshes, this.managers, this.workers);
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

		this.changed.addListener((obs, oldv, newv) -> new Thread(() -> this.updateMeshes(newv)).start());
		this.changed.addListener((obs, oldv, newv) -> changed.set(false));

		this.scaleIndex.set(scaleIndex);
		this.scaleIndex.addListener((obs, oldv, newv) -> changed.set(true));

		this.meshSimplificationIterations.set(meshSimplificationIterations);
		this.meshSimplificationIterations.addListener((obs, oldv, newv) -> changed.set(true));

		this.smoothingLambda.set(smoothingLambda);
		this.smoothingLambda.addListener((obs, oldv, newv) -> changed.set(true));

		this.smoothingIterations.set(smoothingIterations);
		this.smoothingIterations.addListener((obs, oldv, newv) -> changed.set(true));

		this.root = root;

		this.isEnabled.addListener((obs, oldv, newv) -> {
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				synchronized (this.meshes)
				{
					if (newv)
					{
						this.root.getChildren().addAll(this.meshes.values());
					}
					else
					{
						this.root.getChildren().removeAll(this.meshes.values());
					}
				}
			});
		});

		this.meshes.addListener((MapChangeListener<ShapeKey<T>, MeshView>) change -> {
			if (change.wasRemoved())
			{
				((PhongMaterial) change.getValueRemoved().getMaterial()).diffuseColorProperty().unbind();
				change.getValueRemoved().visibleProperty().unbind();
				change.getValueRemoved().drawModeProperty().unbind();
				change.getValueRemoved().cullFaceProperty().unbind();
				change.getValueRemoved().scaleXProperty().unbind();
				change.getValueRemoved().scaleYProperty().unbind();
				change.getValueRemoved().scaleZProperty().unbind();
			}
			else
			{
				((PhongMaterial) change.getValueAdded().getMaterial()).diffuseColorProperty().bind(this
						.colorWithAlpha);
				change.getValueAdded().visibleProperty().bind(this.isVisible);
				change.getValueAdded().drawModeProperty().bind(this.drawMode);
				change.getValueAdded().cullFaceProperty().bind(this.cullFace);
				change.getValueAdded().scaleXProperty().bind(this.inflate);
				change.getValueAdded().scaleYProperty().bind(this.inflate);
				change.getValueAdded().scaleZProperty().bind(this.inflate);
			}

			if (change.wasRemoved())
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> this.root.getChildren().remove(change.getValueRemoved()));
				//					InvokeOnJavaFXApplicationThread.invoke( synchronize( () -> this.root.getChildren()
				// .remove( change.getValueRemoved() ), this.root ) );
			}
			else if (change.wasAdded() && !this.root.getChildren().contains(change.getValueAdded()))
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					if (this.root != null && this.isEnabled.get())
					{
						final ObservableList<Node> children = this.root.getChildren();
						if (!children.contains(change.getValueAdded()))
						{
							LOG.debug("Adding children: {}", change.getValueAdded());
							children.add(change.getValueAdded());
						}
					}
				});
			}
		});
		this.changed.set(true);
	}

	public void interrupt()
	{
		synchronized (this.activeFuture)
		{
			LOG.debug("Canceling task: {}", this.activeFuture);
			Optional.ofNullable(activeFuture.get()).ifPresent(f -> f.cancel(true));
			Optional.ofNullable(activeTask.get()).ifPresent(ManagementTask::interrupt);
			activeFuture.set(null);
			activeTask.set(null);
			this.meshes.clear();
		}
	}

	private void updateMeshes(final boolean doUpdate)
	{
		LOG.debug("Updating mesh? {}", doUpdate);
		if (!doUpdate) { return; }

		synchronized (this.activeFuture)
		{
			interrupt();
			final int scaleIndex = this.scaleIndex.get();
			final Pair<Future<Void>, MeshGeneratorJobManager<T>.ManagementTask> futureAndTask = manager.submit(
					id,
					scaleIndex,
					meshSimplificationIterations.intValue(),
					smoothingLambda.doubleValue(),
					smoothingIterations.intValue(),
					blockListCache[scaleIndex],
					meshCache[scaleIndex],
					submittedTasks::set,
					completedTasks::set,
					() -> {
					}
			                                                                                                  );
			LOG.debug("Submitting new task {}", futureAndTask);
			this.activeFuture.set(futureAndTask.getA());
			this.activeTask.set(futureAndTask.getB());
		}
	}

	private static final Color fromInt(final int argb)
	{
		return Color.rgb(ARGBType.red(argb), ARGBType.green(argb), ARGBType.blue(argb), 1.0);
	}

	public T getId()
	{
		return id;
	}

	public BooleanProperty isEnabledProperty()
	{
		return this.isEnabled;
	}

	public Runnable synchronize(final Runnable r, final Object syncObject)
	{
		return () -> {
			synchronized (syncObject)
			{
				r.run();
			}
		};
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
		LOG.debug("Querying scale index property {}", this.scaleIndex);
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
