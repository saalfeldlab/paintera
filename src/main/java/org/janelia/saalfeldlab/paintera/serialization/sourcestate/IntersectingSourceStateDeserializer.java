package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import bdv.util.volatiles.SharedQueue;
import com.google.gson.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Group;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.IntersectingSourceState;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutorService;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.COMPOSITE_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.COMPOSITE_TYPE_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.MESHES_ENABLED_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.MESHES_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.NAME_KEY;

public class IntersectingSourceStateDeserializer implements JsonDeserializer<IntersectingSourceState>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final IntFunction<SourceState<?, ?>> dependsOn;

	private final SharedQueue queue;

	private final int priority;

	private final Group meshesGroup;

	private final ObjectProperty<ViewFrustum> viewFrustumProperty;

	private final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty;

	private final ObservableBooleanValue viewerEnabled;

	private final ExecutorService manager;

	private final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers;

	public IntersectingSourceStateDeserializer(
			final IntFunction<SourceState<?, ?>> dependsOn,
			final SharedQueue queue,
			final int priority,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final BooleanProperty viewerEnabled,
			final ExecutorService manager,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers)
	{
		super();
		this.dependsOn = dependsOn;
		this.queue = queue;
		this.priority = priority;
		this.meshesGroup = meshesGroup;
		this.viewFrustumProperty = viewFrustumProperty;
		this.eyeToWorldTransformProperty = eyeToWorldTransformProperty;
		this.viewerEnabled = viewerEnabled;
		this.manager = manager;
		this.workers = workers;
	}

	@Plugin(type = StatefulSerializer.DeserializerFactory.class)
	public static class Factory
			implements StatefulSerializer.DeserializerFactory<IntersectingSourceState, IntersectingSourceStateDeserializer>
	{

		@Override
		public IntersectingSourceStateDeserializer createDeserializer(
				final Arguments arguments,
				final Supplier<String> projectDirectory,
				final IntFunction<SourceState<?, ?>> dependencyFromIndex)
		{
			return new IntersectingSourceStateDeserializer(
					dependencyFromIndex,
					arguments.viewer.getQueue(),
					0,
					arguments.viewer.viewer3D().meshesGroup(),
					arguments.viewer.viewer3D().viewFrustumProperty(),
					arguments.viewer.viewer3D().eyeToWorldTransformProperty(),
					arguments.viewer.viewer3D().meshesEnabledProperty(),
					arguments.meshManagerExecutors,
					arguments.meshWorkersExecutors);
		}

		@Override
		public Class<IntersectingSourceState> getTargetClass() {
			return IntersectingSourceState.class;
		}
	}

	@Override
	public IntersectingSourceState deserialize(final JsonElement el, final Type type, final JsonDeserializationContext
			context)
	throws JsonParseException
	{
		final JsonObject map = el.getAsJsonObject();
		LOG.debug("Deserializing {}", map);
		final int[] dependsOn = context.deserialize(map.get(SourceStateSerialization.DEPENDS_ON_KEY), int[].class);

		if (dependsOn.length != 2)
		{
			throw new JsonParseException("Expected exactly two dependencies, got: " + map.get(SourceStateSerialization.DEPENDS_ON_KEY));
		}

		final SourceState<?, ?> thresholdedState = this.dependsOn.apply(dependsOn[0]);
		final SourceState<?, ?> labelState       = this.dependsOn.apply(dependsOn[1]);
		if (thresholdedState == null || labelState == null) { return null; }

		if (!(thresholdedState instanceof ThresholdingSourceState<?, ?>))
		{
			throw new JsonParseException("Expected " + ThresholdingSourceState.class.getName() + " as first " +
					"dependency but got " + thresholdedState.getClass().getName() + " instead.");
		}

		if (!(labelState instanceof ConnectomicsLabelState || labelState instanceof LabelSourceState<?, ?>))
		{
			throw new JsonParseException("Expected "
					+ ConnectomicsLabelState.class.getName() + " or "
					+ LabelSourceState.class.getName() + " as second dependency but got "
					+ labelState.getClass().getName() + " instead.");
		}

		try
		{
			final Class<? extends Composite<ARGBType, ARGBType>> compositeType =
					(Class<Composite<ARGBType, ARGBType>>) Class.forName(map.get(COMPOSITE_TYPE_KEY).getAsString());
			final Composite<ARGBType, ARGBType> composite = context.deserialize(map.get(COMPOSITE_KEY), compositeType);

			final String name = map.get(NAME_KEY).getAsString();


			LOG.debug(
					"Creating {} with thresholded={} labels={}",
					IntersectingSourceState.class.getSimpleName(),
					thresholdedState,
					labelState);

			final IntersectingSourceState state;
			if (labelState instanceof ConnectomicsLabelState<?, ?>)
				state = new IntersectingSourceState(
						(ThresholdingSourceState) thresholdedState,
						(ConnectomicsLabelState) labelState,
						composite,
						name,
						queue,
						priority,
						meshesGroup,
						viewFrustumProperty,
						eyeToWorldTransformProperty,
						viewerEnabled,
						manager,
						workers);
			else if (labelState instanceof LabelSourceState<?, ?>)
				state = new IntersectingSourceState(
						(ThresholdingSourceState) thresholdedState,
						(LabelSourceState) labelState,
						composite,
						name,
						queue,
						priority,
						meshesGroup,
						viewFrustumProperty,
						eyeToWorldTransformProperty,
						viewerEnabled,
						manager,
						workers);
			else
				throw new JsonParseException("Expected "
						+ ConnectomicsLabelState.class.getName() + " or "
						+ LabelSourceState.class.getName() + " as second dependency but got "
						+ labelState.getClass().getName() + " instead.");
			if (map.has(MESHES_KEY) && map.get(MESHES_KEY).isJsonObject()) {
				final JsonObject meshesMap = map.get(MESHES_KEY).getAsJsonObject();
				if (meshesMap.has(MESHES_ENABLED_KEY) && meshesMap.get(MESHES_ENABLED_KEY).isJsonPrimitive())
					state.setMeshesEnabled(meshesMap.get(MESHES_ENABLED_KEY).getAsBoolean());
			}
			return state;

		} catch (final ClassNotFoundException e)
		{
			throw new JsonParseException(e);
		}
	}

}
