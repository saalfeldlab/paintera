package org.janelia.saalfeldlab.paintera.data;

import javafx.beans.property.ObjectProperty;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;

public interface HasModifiableAxisOrder extends HasAxisOrder {

	ObjectProperty<AxisOrder> axisOrderProperty();

	default void setAxisOrder(AxisOrder to)
	{
		axisOrderProperty().set(to);
	}

	@Override
	default AxisOrder getAxisOrder()
	{
		return axisOrderProperty().get();
	}

}
