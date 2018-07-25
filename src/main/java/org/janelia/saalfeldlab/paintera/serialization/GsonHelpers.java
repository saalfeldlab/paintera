package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.invoke.MethodHandles;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import com.google.gson.GsonBuilder;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSourceDeserializer;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSourceSerializer;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceDeserializer;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceSerializer;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.serialization.config.CrosshairConfigSerializer;
import org.janelia.saalfeldlab.paintera.serialization.config.MeshSettingsSerializer;
import org.janelia.saalfeldlab.paintera.serialization.converter.ARGBColorConverterSerializer;
import org.janelia.saalfeldlab.paintera.serialization.converter.HighlightingStreamConverterSerializer;
import org.janelia.saalfeldlab.paintera.serialization.fx.SimpleBooleanPropertySerializer;
import org.janelia.saalfeldlab.paintera.serialization.fx.SimpleDoublePropertySerializer;
import org.janelia.saalfeldlab.paintera.serialization.fx.SimpleIntegerPropertySerializer;
import org.janelia.saalfeldlab.paintera.serialization.fx.SimpleLongPropertySerializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateDeserializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.InvertingSourceStateDeserializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.InvertingSourceStateSerializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.LabelSourceStateDeserializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.LabelSourceStateSerializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.RawSourceStateDeserializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.RawSourceStateSerializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.ThresholdingSourceStateDeserializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.ThresholdingSourceStateSerializer;
import org.janelia.saalfeldlab.paintera.state.IntersectingSourceState;
import org.janelia.saalfeldlab.paintera.state.InvertingRawSourceState;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GsonHelpers
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static GsonBuilder builderWithAllRequiredDeserializers(
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory)
	{
		final IntFunction<SourceState<?, ?>> dependencyFromIndex = index -> viewer.sourceInfo().getState(viewer
				.sourceInfo().trackSources().get(
				index));
		return builderWithAllRequiredDeserializers(new Arguments(viewer), projectDirectory, dependencyFromIndex);
	}

	public static GsonBuilder builderWithAllRequiredDeserializers(
			final Arguments arguments,
			final Supplier<String> projectDirectory,
			final IntFunction<SourceState<?, ?>> dependencyFromIndex)
	{
		return new GsonBuilder()
				.registerTypeAdapter(N5DataSource.class, new N5DataSourceDeserializer(arguments.sharedQueue, 0))
				.registerTypeAdapter(AffineTransform3D.class, new AffineTransform3DJsonAdapter())
				.registerTypeHierarchyAdapter(ARGBColorConverter.class, new ARGBColorConverterSerializer<>())
				.registerTypeHierarchyAdapter(
						HighlightingStreamConverter.class,
						new HighlightingStreamConverterSerializer()
				                             )
				.registerTypeAdapter(
						MaskedSource.class,
						new MaskedSourceDeserializer(projectDirectory, arguments.propagationWorkers)
				                    )
				.registerTypeAdapter(RawSourceState.class, new RawSourceStateDeserializer())
				.registerTypeAdapter(SelectedIds.class, new SelectedIdsSerializer())
				.registerTypeAdapter(CommitCanvasN5.class, new CommitCanvasN5Serializer())
				.registerTypeAdapter(
						InvertingRawSourceState.class,
						new InvertingSourceStateDeserializer(dependencyFromIndex)
				                    )
				.registerTypeAdapter(
						ThresholdingSourceState.class,
						new ThresholdingSourceStateDeserializer(dependencyFromIndex)
				                    )
				.registerTypeAdapter(
						IntersectingSourceState.class,
						new IntersectingSourceStateDeserializer.Factory().createDeserializer(arguments,
								projectDirectory,
								dependencyFromIndex
						                                                                    )
				                    )
				.registerTypeAdapter(SimpleDoubleProperty.class, new SimpleDoublePropertySerializer())
				.registerTypeAdapter(CrosshairConfig.class, new CrosshairConfigSerializer())
				.registerTypeAdapter(SimpleBooleanProperty.class, new SimpleBooleanPropertySerializer())
				.registerTypeAdapter(SimpleIntegerProperty.class, new SimpleIntegerPropertySerializer())
				.registerTypeAdapter(SimpleLongProperty.class, new SimpleLongPropertySerializer())
				.registerTypeAdapter(ManagedMeshSettings.class, ManagedMeshSettings.jsonSerializer())
				.registerTypeAdapter(MeshSettings.class, new MeshSettingsSerializer())
				.registerTypeAdapter(LabelSourceState.class, new LabelSourceStateDeserializer<>(arguments));
	}

	public static GsonBuilder builderWithAllRequiredSerializers(
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory)
	{
		final ToIntFunction<SourceState<?, ?>> dependencyFromIndex =
				state -> viewer.sourceInfo().trackSources().indexOf(state.getDataSource());
		return builderWithAllRequiredSerializers(projectDirectory, dependencyFromIndex);
	}

	public static GsonBuilder builderWithAllRequiredSerializers(
			final Supplier<String> projectDirectory,
			final ToIntFunction<SourceState<?, ?>> dependencyToIndex)
	{
		LOG.debug("Creating builder with required serializers.");
		return new GsonBuilder()
				.registerTypeAdapter(AffineTransform3D.class, new AffineTransform3DJsonAdapter())
				.registerTypeAdapter(WindowProperties.class, new WindowPropertiesSerializer())
				.registerTypeAdapter(RawSourceState.class, new RawSourceStateSerializer())
				.registerTypeHierarchyAdapter(
						HighlightingStreamConverter.class,
						new HighlightingStreamConverterSerializer()
				                             )
				.registerTypeHierarchyAdapter(Composite.class, new CompositeSerializer())
				.registerTypeAdapter(N5DataSource.class, new N5DataSourceSerializer())
				.registerTypeAdapter(LabelSourceState.class, new LabelSourceStateSerializer())
				.registerTypeAdapter(SourceInfo.class, new SourceInfoSerializer())
				.registerTypeAdapter(MaskedSource.class, new MaskedSourceSerializer())
				.registerTypeHierarchyAdapter(ARGBColorConverter.class, new ARGBColorConverterSerializer<>())
				.registerTypeAdapter(SelectedIds.class, new SelectedIdsSerializer())
				.registerTypeAdapter(CommitCanvasN5.class, new CommitCanvasN5Serializer())
				.registerTypeAdapter(
						FragmentSegmentAssignmentOnlyLocal.class,
						new FragmentSegmentAssignmentOnlyLocalSerializer()
				                    )
				.registerTypeAdapter(
						ThresholdingSourceState.class,
						new ThresholdingSourceStateSerializer(dependencyToIndex)
				                    )
				.registerTypeAdapter(
						IntersectingSourceState.class,
						new IntersectingSourceStateSerializer(dependencyToIndex)
				                    )
				.registerTypeAdapter(SimpleDoubleProperty.class, new SimpleDoublePropertySerializer())
				.registerTypeAdapter(CrosshairConfig.class, new CrosshairConfigSerializer())
				.registerTypeAdapter(SimpleBooleanProperty.class, new SimpleBooleanPropertySerializer())
				.registerTypeAdapter(SimpleIntegerProperty.class, new SimpleIntegerPropertySerializer())
				.registerTypeAdapter(SimpleLongProperty.class, new SimpleLongPropertySerializer())
				.registerTypeAdapter(ManagedMeshSettings.class, ManagedMeshSettings.jsonSerializer())
				.registerTypeAdapter(MeshSettings.class, new MeshSettingsSerializer())
				.registerTypeAdapter(
						InvertingRawSourceState.class,
						new InvertingSourceStateSerializer(dependencyToIndex)
				                    );
	}

}
