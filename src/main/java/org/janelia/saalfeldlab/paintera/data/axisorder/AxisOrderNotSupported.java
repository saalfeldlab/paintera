package org.janelia.saalfeldlab.paintera.data.axisorder;

import java.util.Arrays;

public class AxisOrderNotSupported extends Exception {

	private final AxisOrder illegalAxisOrder;

	private final AxisOrder[] supportedAxisOrders;

	public AxisOrderNotSupported (final AxisOrder getIllegalAxisOrder, AxisOrder... supportedAxisOrders)
	{
		super(String.format("AxisOrder not supported: {}. Use any of {} instead.", Arrays.toString(supportedAxisOrders)));
		this.illegalAxisOrder = getIllegalAxisOrder;
		this.supportedAxisOrders = supportedAxisOrders;
	}

	public AxisOrder getIllegalAxisOrder() {
		return illegalAxisOrder;
	}

	public AxisOrder[] getSupportedAxisOrders()
	{
		return supportedAxisOrders.clone();
	}
}
