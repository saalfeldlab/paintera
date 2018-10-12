package org.janelia.saalfeldlab.util.math;

import org.janelia.saalfeldlab.paintera.exception.PainteraException;

public class DoubleHasNonIntegralValue extends PainteraException {

	public final double doubleValue;

	public DoubleHasNonIntegralValue(double doubleValue) {
		super(String.format("%f is not an integral value.", doubleValue));
		this.doubleValue = doubleValue;
	}
}
