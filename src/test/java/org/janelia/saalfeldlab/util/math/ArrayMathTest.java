package org.janelia.saalfeldlab.util.math;

import org.junit.Assert;
import org.junit.Test;

import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class ArrayMathTest {

	@Test
	public void multiplyElementwise3() {
		long[] f1 = {1, 2, 3};
		long[] f2 = {6, 5, 4};
		Assert.assertArrayEquals(new long[] {6, 10, 12}, ArrayMath.multiplyElementwise3(f1, f2));
		Assert.assertArrayEquals(new long[] {6, 10, 12}, ArrayMath.multiplyElementwise3(f1, LongStream.of(f2).mapToInt(l -> (int)l).toArray()));
	}

	@Test
	public void asDoubleArray3() {
		Assert.assertArrayEquals(new double[] {1, 2, 3}, ArrayMath.asDoubleArray3(new long[] {1, 2, 3}), 0.0);
	}

	@Test
	public void add3DoubleInt() {
		final double[] s1 = {1.0, 2.0, 3.0};
		final int[] s2 = {8, 6, 4};
		Assert.assertArrayEquals(new double[] {9.0, 8.0, 7.0}, ArrayMath.add3(s1, s2), 0.0);
	}

	@Test
	public void add3LongInt() {
		final long[] s1 = {1, 2, 3};
		final int[] s2 = {8, 6, 4};
		Assert.assertArrayEquals(new long[] {9, 8, 7}, ArrayMath.add3(s1, s2));
	}

	@Test
	public void add3Scalar() {
		final long[] s1 = {1, 2, 3};
		Assert.assertArrayEquals(new long[] {2, 3, 4}, ArrayMath.add3(s1, 1));
	}

	@Test
	public void divide3() {
		{
			final long[] divident = {5, 2, 1};
			final long[] divisor = {2, 3, 1};
			Assert.assertArrayEquals(new long[]{2, 0, 1}, ArrayMath.divide3(divident, divisor));
			Assert.assertArrayEquals(new long[]{2, 0, 1}, ArrayMath.divide3(divident, LongStream.of(divisor).mapToInt(l -> (int) l).toArray()));
		}

		{
			double[] divident = {3.0, 1.5, 2.3};
			double[] divisor = {2.0, 1.5, 6.9};
			double[] quotient = ArrayMath.divide3(divident, divisor);
			Assert.assertArrayEquals(new double[]{1.5, 1.0, 1.0 / 3.0}, quotient, 0.0);
		}
	}

	@Test
	public void minOf3() {
		{
			final long[] arr1 = {1, 3, 2};
			final long[] arr2 = {2, 2, 2};
			Assert.assertArrayEquals(new long[]{1, 2, 2}, ArrayMath.minOf3(arr1, arr2));
			Assert.assertArrayEquals(new long[]{1, 2, 2}, ArrayMath.minOf3(LongStream.of(arr1).asDoubleStream().toArray(), arr2));
		}

		{
			final int[] arr1 = {1, 3, 2};
			final int[] arr2 = {2, 2, 2};
			Assert.assertArrayEquals(new int[]{1, 2, 2}, ArrayMath.minOf3(arr1, arr2));
		}
	}

	@Test
	public void ceilFloor()
	{
		final double[] arr = {1.0, 1.3, 2.0};
		final double[] ceil = ArrayMath.ceil3(arr);
		final double[] floor = ArrayMath.floor3(arr);
		Assert.assertArrayEquals(new double[] {1.0, 2.0, 2.0}, ceil, 0.0);
		Assert.assertArrayEquals(new double[] {1.0, 1.0, 2.0}, floor, 0.0);
	}

	@Test
	public void asLong()
	{
		final double[] arr = {1.34, 3535.999, 14.0};
		long[] integralLong = ArrayMath.asLong3(arr);
		Assert.assertArrayEquals(new long[] {1, 3535, 14}, integralLong);
	}

	@Test
	public void asInt() throws DoubleHasNonIntegralValue {
		{
			final double[] arr = {1.34, 3535.999, 14.0};
			int[] integralLong = ArrayMath.asInt3(arr, false);
			Assert.assertArrayEquals(new int[]{1, 3535, 14}, integralLong);
		}

		{
			final double[] arr = {1., 3535., 14.};
			int[] integralLong = ArrayMath.asInt3(arr, true);
			Assert.assertArrayEquals(new int[]{1, 3535, 14}, integralLong);
		}
	}

	@Test(expected = DoubleHasNonIntegralValue.class)
	public void asIntFail() throws DoubleHasNonIntegralValue {
		final double[] arr = {1.34, 3535.999, 14.0};
		int[] integralLong = ArrayMath.asInt3(arr, true);
		Assert.assertArrayEquals(new int[] {1, 3535, 14}, integralLong);
	}

}
