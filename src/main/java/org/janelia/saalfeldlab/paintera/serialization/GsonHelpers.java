package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.GsonBuilder;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.util.Pair;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSourceDeserializer;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSourceSerializer;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSourceDeserializer;
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSourceSerializer;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceDeserializer;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceSerializer;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.serialization.config.CrosshairConfigSerializer;
import org.janelia.saalfeldlab.paintera.serialization.config.MeshSettingsSerializer;
import org.janelia.saalfeldlab.paintera.serialization.converter.ARGBColorConverterSerializer;
import org.janelia.saalfeldlab.paintera.serialization.converter.ARGBCompositeColorConverterSerializer;
import org.janelia.saalfeldlab.paintera.serialization.converter.HighlightingStreamConverterSerializer;
import org.janelia.saalfeldlab.paintera.serialization.fx.SimpleBooleanPropertySerializer;
import org.janelia.saalfeldlab.paintera.serialization.fx.SimpleDoublePropertySerializer;
import org.janelia.saalfeldlab.paintera.serialization.fx.SimpleIntegerPropertySerializer;
import org.janelia.saalfeldlab.paintera.serialization.fx.SimpleLongPropertySerializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.ChannelSourceStateDeserializer;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.ChannelSourceStateSerializer;
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
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState;
import org.janelia.saalfeldlab.paintera.state.IntersectingSourceState;
import org.janelia.saalfeldlab.paintera.state.InvertingRawSourceState;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.scijava.InstantiableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public class GsonHelpers {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static GsonBuilder builderWithAllRequiredDeserializers(
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) throws InstantiableException {
		final IntFunction<SourceState<?, ?>> dependencyFromIndex = index -> viewer.sourceInfo().getState(viewer
				.sourceInfo().trackSources().get(
						index));
		return builderWithAllRequiredDeserializers(new Arguments(viewer), projectDirectory, dependencyFromIndex);
	}

	public static GsonBuilder builderWithAllRequiredDeserializers(
			final Arguments arguments,
			final Supplier<String> projectDirectory,
			final IntFunction<SourceState<?, ?>> dependencyFromIndex) throws InstantiableException {

		final Map<Class<?>, List<Pair<PainteraSerialization.PainteraDeserializer, Double>>> deserializers = PainteraSerialization.getDeserializers();
		final Map<Class<?>, List<Pair<StatefulSerializer.DeserializerFactory, Double>>> deserializerFactories = StatefulSerializer.getDeserializers();

		final GsonBuilder builder = new GsonBuilder();

		for (final Map.Entry<Class<?>, List<Pair<PainteraSerialization.PainteraDeserializer, Double>>> d : deserializers.entrySet())
		{
			final PainteraSerialization.PainteraDeserializer<?> v = d.getValue().get(0).getKey();
			LOG.debug("Adding deserializer for class {} ({})", d.getKey(), v.isHierarchyAdapter());
			if (v.isHierarchyAdapter())
				builder.registerTypeHierarchyAdapter(d.getKey(), v);
			else
				builder.registerTypeAdapter(d.getKey(), v);
		}

		for (final Map.Entry<Class<?>, List<Pair<StatefulSerializer.DeserializerFactory, Double>>> d : deserializerFactories.entrySet())
		{
			final StatefulSerializer.DeserializerFactory<?, ?> v = d.getValue().get(0).getKey();
			LOG.debug("Adding deserializer factory for class {}", d.getKey());
			builder.registerTypeAdapter(d.getKey(), v.createDeserializer(arguments, projectDirectory, dependencyFromIndex));
		}

		return builder;

	}

	public static GsonBuilder builderWithAllRequiredSerializers(
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) {
		final ToIntFunction<SourceState<?, ?>> dependencyFromIndex =
				state -> viewer.sourceInfo().trackSources().indexOf(state.getDataSource());
		return builderWithAllRequiredSerializers(projectDirectory, dependencyFromIndex);
	}

	public static GsonBuilder builderWithAllRequiredSerializers(
			final Supplier<String> projectDirectory,
			final ToIntFunction<SourceState<?, ?>> dependencyToIndex) {
		LOG.debug("Creating builder with required serializers.");

		final Map<Class<?>, List<Pair<PainteraSerialization.PainteraSerializer, Double>>> serializers = PainteraSerialization.getSerializers();
		final Map<Class<?>, List<Pair<StatefulSerializer.SerializerFactory, Double>>> serializerFactories = StatefulSerializer.getSerializers();
		LOG.debug("Found serializer factories for these classes: {}", serializerFactories.keySet());

		final GsonBuilder builder = new GsonBuilder();

		for (final Map.Entry<Class<?>, List<Pair<PainteraSerialization.PainteraSerializer, Double>>> d : serializers.entrySet())
		{
			final PainteraSerialization.PainteraSerializer v = d.getValue().get(0).getKey();
			LOG.debug("Adding serializer for class {} ({})", d.getKey(), v.isHierarchyAdapter());
			if (v.isHierarchyAdapter())
				builder.registerTypeHierarchyAdapter(d.getKey(), v);
			else
				builder.registerTypeAdapter(d.getKey(), v);
		}

		for (final Map.Entry<Class<?>, List<Pair<StatefulSerializer.SerializerFactory, Double>>> d : serializerFactories.entrySet())
		{
			final StatefulSerializer.SerializerFactory v = d.getValue().get(0).getKey();
			LOG.debug("Adding serializer factory for class {}", d.getKey());
			builder.registerTypeAdapter(d.getKey(), v.createSerializer(projectDirectory, dependencyToIndex));
		}

		return builder;
	}

}
