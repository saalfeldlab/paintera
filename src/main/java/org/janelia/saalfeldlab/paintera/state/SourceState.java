package org.janelia.saalfeldlab.paintera.state;

import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;

public interface SourceState<D, T>
{

	public DataSource<D, T> getDataSource();

	public Converter<T, ARGBType> converter();

	public ObjectProperty<Composite<ARGBType, ARGBType>> compositeProperty();

	public StringProperty nameProperty();

	public BooleanProperty isVisibleProperty();

	public ObservableBooleanValue isDirtyProperty();

	public ObjectProperty<Interpolation> interpolationProperty();

	public SourceState<?, ?>[] dependsOn();

	public void stain();

	public void clean();

	public boolean isDirty();

	public default SourceAndConverter<T> getSourceAndConverter()
	{
		return new SourceAndConverter<>(getDataSource(), converter());
	}

}
