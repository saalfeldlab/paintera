package org.janelia.saalfeldlab.paintera.data.axisorder;

import java.util.Arrays;

public class AxisOrderNotSupported extends Exception {

	private final AxisOrder illegalAxisOrder;

	private final AxisOrder[] supportedAxisOrders;


	public AxisOrderNotSupported (final String message, final AxisOrder illegalAxisOrder, AxisOrder... supportedAxisOrders)
	{
		super(message);
		this.illegalAxisOrder = illegalAxisOrder;
		this.supportedAxisOrders = supportedAxisOrders;
	}

	public AxisOrderNotSupported (final AxisOrder illegalAxisOrder, AxisOrder... supportedAxisOrders)
	{
		this(
				String.format("Axis order not supported: {}. Use any of {} instead.", Arrays.toString(supportedAxisOrders)),
				illegalAxisOrder,
				supportedAxisOrders);
	}

	public AxisOrder getIllegalAxisOrder() {
		return illegalAxisOrder;
	}

	public AxisOrder[] getSupportedAxisOrders()
	{
		return supportedAxisOrders.clone();
	}
}
