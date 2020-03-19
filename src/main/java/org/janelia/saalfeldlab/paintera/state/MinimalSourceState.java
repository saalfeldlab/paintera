package org.janelia.saalfeldlab.paintera.state;

import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class MinimalSourceState<D, T, S extends DataSource<D, T>, C extends Converter<T, ARGBType>>
		implements SourceState<D, T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final S dataSource;

	private final C converter;

	private final ObjectProperty<Composite<ARGBType, ARGBType>> composite;

	private final StringProperty name;

	private final StringProperty statusText = new SimpleStringProperty(null);

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

	private final ObjectProperty<Interpolation> interpolation = new SimpleObjectProperty<>(Interpolation.NEARESTNEIGHBOR);

	private final SourceState<?, ?>[] dependsOn;

	public MinimalSourceState(
			final S dataSource,
			final C converter,
			final Composite<ARGBType, ARGBType> composite,
			final String name,
			final SourceState<?, ?>... dependsOn)
	{
		super();
		LOG.debug(
				"Creating minimal source state with dataSource={} converter={} composite={} name={} dependsOn={}",
				dataSource,
				converter,
				composite,
				name,
				dependsOn);
		this.dataSource = dataSource;
		this.converter = converter;
		this.composite = new SimpleObjectProperty<>(composite);
		this.name = new SimpleStringProperty(name);
		this.dependsOn = Arrays
				.stream(dependsOn)
				.filter(d -> !this.equals(d))
				.toArray(SourceState[]::new);

	}

	@Override
	public C converter()
	{
		return this.converter;
	}

	@Override
	public ObjectProperty<Composite<ARGBType, ARGBType>> compositeProperty()
	{
		return this.composite;
	}

	@Override
	public StringProperty nameProperty()
	{
		return this.name;
	}

	@Override
	public StringProperty statusTextProperty()
	{
		return this.statusText;
	}

	@Override
	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	@Override
	public ObjectProperty<Interpolation> interpolationProperty()
	{
		return this.interpolation;
	}

	public Converter<T, ARGBType> getConverter()
	{
		return this.converter;
	}

	@Override
	public SourceAndConverter<T> getSourceAndConverter()
	{
		final SourceAndConverter<T> sac = new SourceAndConverter<>(dataSource, converter);
		return sac;
	}

	@Override
	public S getDataSource()
	{
		return this.dataSource;
	}

	public SourceState<?, ?>[] getDependsOn()
	{
		return this.dependsOn.clone();
	}

	@Override
	public SourceState<?, ?>[] dependsOn()
	{
		return this.getDependsOn();
	}

	@Override
	public Node getDisplayStatus()
	{
		return null;
	}

	@Override
	public KeyAndMouseBindings createKeyAndMouseBindings() {
		// TODO: Implement as needed
		return new KeyAndMouseBindings();
	}
}
