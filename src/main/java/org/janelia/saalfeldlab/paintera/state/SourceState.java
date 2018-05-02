package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;

import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class SourceState< D, T >
{

	private final DataSource< D, T > dataSource;

	private final Converter< T, ARGBType > converter;

	private final ObjectProperty< Composite< ARGBType, ARGBType > > composite;

	private final StringProperty name;

	private final Object metaData;

	private final BooleanProperty isVisible = new SimpleBooleanProperty( true );

	private final BooleanProperty isDirty = new SimpleBooleanProperty( false );

	private final ObjectProperty< Interpolation > interpolation = new SimpleObjectProperty<>( Interpolation.NEARESTNEIGHBOR );

	public SourceState(
			final DataSource< D, T > dataSource,
			final Converter< T, ARGBType > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final Object info )
	{
		super();
		this.dataSource = dataSource;
		this.converter = converter;
		this.composite = new SimpleObjectProperty<>( composite );
		this.name = new SimpleStringProperty( name );
		this.metaData = info;

		this.composite.addListener( obs -> this.stain() );
		this.name.addListener( obs -> this.stain() );
		this.isVisible.addListener( obs -> this.stain() );

	}

	public DataSource< D, T > dataSource()
	{
		return this.dataSource;
	}

	public Converter< T, ARGBType > converter()
	{
		return this.converter;
	}

	public ObjectProperty< Composite< ARGBType, ARGBType > > compositeProperty()
	{
		return this.composite;
	}

	public StringProperty nameProperty()
	{
		return this.name;
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	public void stain()
	{
		this.isDirty.set( true );
	}

	public void clean()
	{
		this.isDirty.set( false );
	}

	public boolean isDirty()
	{
		return this.isDirty.get();
	}

	public ObservableBooleanValue isDirtyProperty()
	{
		return this.isDirty;
	}

	public Object getMetaData()
	{
		return metaData;
	}

	public ObjectProperty< Interpolation > interpolationProperty()
	{
		return this.interpolation;
	}

	public Converter< T, ARGBType > getConverter()
	{
		return this.converter;
	}

	public SourceAndConverter< T > getSourceAndConverter()
	{
		final SourceAndConverter< T > sac = new SourceAndConverter<>( dataSource, converter );
		return sac;
	}

	public DataSource< D, T > getDataSource()
	{
		return this.dataSource;
	}

}
