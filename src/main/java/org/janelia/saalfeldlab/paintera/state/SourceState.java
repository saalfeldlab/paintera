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
import org.janelia.saalfeldlab.paintera.data.HasModifiableAxisOrder;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;

public interface SourceState<D, T> extends HasModifiableAxisOrder
{

	DataSource<D, T> getDataSource();

	Converter<T, ARGBType> converter();

	ObjectProperty<Composite<ARGBType, ARGBType>> compositeProperty();

	StringProperty nameProperty();

	BooleanProperty isVisibleProperty();

	ObservableBooleanValue isDirtyProperty();

	ObjectProperty<Interpolation> interpolationProperty();

	SourceState<?, ?>[] dependsOn();

	void stain();

	void clean();

	boolean isDirty();

	default SourceAndConverter<T> getSourceAndConverter()
	{
		return new SourceAndConverter<>(getDataSource(), converter());
	}

}
