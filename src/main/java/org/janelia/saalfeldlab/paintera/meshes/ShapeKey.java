package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;

public class ShapeKey<T>
{

	private final T shapeId;

	private final int scaleIndex;

	private final int simplificationIterations;

	private final double smoothingLambda;

	private final int smoothingIterations;

	private final long[] min;

	private final long[] max;

	public ShapeKey(
			final T shapeId,
			final int scaleIndex,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final long[] min,
			final long[] max)
	{
		this.shapeId = shapeId;
		this.scaleIndex = scaleIndex;
		this.simplificationIterations = simplificationIterations;
		this.smoothingLambda = smoothingLambda;
		this.smoothingIterations = smoothingIterations;
		this.min = min;
		this.max = max;
	}

	@Override
	public String toString()
	{
		return String.format(
				"{shapeId=%s, scaleIndex=%d, simplifications=%d, smoothingLambda=%f, smoothings=%d, min=%s, max=%s}",
				shapeId,
				scaleIndex,
				simplificationIterations,
				smoothingLambda,
				smoothingIterations,
				Arrays.toString(min), Arrays.toString(max)
		                    );
	}

	@Override
	public int hashCode()
	{
		int result = scaleIndex;
		result = 31 * result + shapeId.hashCode();
		result = 31 * result + simplificationIterations;
		result = 31 * result + Double.hashCode(smoothingLambda);
		result = 31 * result + smoothingIterations;
		result = 31 * result + Arrays.hashCode(this.min);
		result = 31 * result + Arrays.hashCode(this.max);
		return result;
	}

	@Override
	public boolean equals(final Object other)
	{
		if (other instanceof ShapeKey<?>)
		{
			final ShapeKey<?> otherShapeKey = (ShapeKey<?>) other;
			return shapeId.equals(otherShapeKey.shapeId) &&
					otherShapeKey.scaleIndex == scaleIndex &&
					otherShapeKey.simplificationIterations == this.simplificationIterations &&
					otherShapeKey.smoothingLambda == this.smoothingLambda &&
					otherShapeKey.smoothingIterations == this.smoothingIterations &&
					Arrays.equals(otherShapeKey.min, min) &&
					Arrays.equals(otherShapeKey.max, max);
		}
		return false;
	}

	public T shapeId()
	{
		return shapeId;
	}

	public int scaleIndex()
	{
		return scaleIndex;
	}

	public int simplificationIterations()
	{
		return simplificationIterations;
	}

	public double smoothingLambda()
	{
		return smoothingLambda;
	}

	public int smoothingIterations()
	{
		return smoothingIterations;
	}

	public long[] min()
	{
		return min.clone();
	}

	public long[] max()
	{
		return max.clone();
	}

	public void min(final long[] min)
	{
		System.arraycopy(this.min, 0, min, 0, min.length);
	}

	public void max(final long[] max)
	{
		System.arraycopy(this.max, 0, max, 0, max.length);
	}

	public Interval interval()
	{
		return new FinalInterval(min, max);
	}

}