package org.janelia.saalfeldlab.util.math;

public class ArrayMath {

	public static long[] multiplyElementwise3(final long[] factor1, final long[] factor2)
	{
		return multiplyElementwise3(factor1, factor2, new long[3]);
	}

	public static long[] multiplyElementwise3(final long[] factor1, final long[] factor2, final long[] product)
	{
		product[0] = factor1[0] * factor2[0];
		product[1] = factor1[1] * factor2[1];
		product[2] = factor1[2] * factor2[2];
		return product;
	}

	public static long[] multiplyElementwise3(final long[] factor1, final int[] factor2)
	{
		return multiplyElementwise3(factor1, factor2, new long[3]);
	}

	public static long[] multiplyElementwise3(final long[] factor1, final int[] factor2, final long[] product)
	{
		product[0] = factor1[0] * factor2[0];
		product[1] = factor1[1] * factor2[1];
		product[2] = factor1[2] * factor2[2];
		return product;
	}

	public static double[] asDoubleArray3(final long[] source)
	{
		return asDoubleArray3(source, new double[3]);
	}

	public static double[] asDoubleArray3(final long[] source, final double[] target)
	{
		target[0] = source[0];
		target[1] = source[1];
		target[2] = source[2];
		return target;
	}

	public static double[] add3(final double[] summand1, final int[] summand2)
	{
		return add3(summand1, summand2, new double[3]);
	}

	public static double[] add3(final double[] summand1, final int[] summand2, final double[] sum)
	{
		sum[0] = summand1[0] + summand2[0];
		sum[1] = summand1[1] + summand2[1];
		sum[2] = summand1[2] + summand2[2];
		return sum;
	}

	public static long[] add3(final long[] summand1, final int[] summand2)
	{
		return add3(summand1, summand2, new long[3]);
	}

	public static long[] add3(final long[] summand1, final int[] summand2, final long[] sum)
	{
		sum[0] = summand1[0] + summand2[0];
		sum[1] = summand1[1] + summand2[1];
		sum[2] = summand1[2] + summand2[2];
		return sum;
	}

	public static long[] add3(final long[] summand1, final long summand2)
	{
		return add3(summand1, summand2, new long[3]);
	}

	public static long[] add3(final long[] summand1, final long summand2, final long[] sum)
	{
		sum[0] = summand1[0] + summand2;
		sum[1] = summand1[1] + summand2;
		sum[2] = summand1[2] + summand2;
		return sum;
	}

	public static long[] divide3(final long[] divident, final long[] divisor)
	{
		return divide3(divident, divisor, new long[3]);
	}

	public static long[] divide3(final long[] divident, final int[] divisor)
	{
		return divide3(divident, divisor, new long[3]);
	}

	public static long[] divide3(final long[] divident, final long[] divisor, final long[] quotient)
	{
		quotient[0] = divident[0] / divisor[0];
		quotient[1] = divident[1] / divisor[1];
		quotient[2] = divident[2] / divisor[2];
		return quotient;
	}

	public static long[] divide3(final long[] divident, final int[] divisor, final long[] quotient)
	{
		quotient[0] = divident[0] / divisor[0];
		quotient[1] = divident[1] / divisor[1];
		quotient[2] = divident[2] / divisor[2];
		return quotient;
	}

	public static double[] divide3(final double[] divident, final double[] divisor)
	{
		return divide3(divident, divisor, new double[3]);
	}

	public static double[] divide3(final double[] divident, final double[] divisor, final double[] quotient)
	{
		quotient[0] = divident[0] / divisor[0];
		quotient[1] = divident[1] / divisor[1];
		quotient[2] = divident[2] / divisor[2];
		return quotient;
	}

	public static long[] minOf3(final double[] arr1, final long[] arr2)
	{
		return minOf3(arr1, arr2, new long[3]);
	}

	public static long[] minOf3(final long[] arr1, final long[] arr2)
	{
		return minOf3(arr1, arr2, new long[3]);
	}

	public static long[] minOf3(final double[] arr1, final long[] arr2, final long[] min)
	{
		min[0] = Math.min((long) arr1[0], arr2[0]);
		min[1] = Math.min((long) arr1[1], arr2[1]);
		min[2] = Math.min((long) arr1[2], arr2[2]);
		return min;
	}

	public static long[] minOf3(final long[] arr1, final long[] arr2, final long[] min)
	{
		min[0] = Math.min(arr1[0], arr2[0]);
		min[1] = Math.min(arr1[1], arr2[1]);
		min[2] = Math.min(arr1[2], arr2[2]);
		return min;
	}

	public static int[] minOf3(final int[] arr1, final int[] arr2)
	{
		return minOf3(arr1, arr2, new int[3]);
	}

	public static int[] minOf3(final int[] arr1, final int[] arr2, final int[] min)
	{
		min[0] = Math.min(arr1[0], arr2[0]);
		min[1] = Math.min(arr1[1], arr2[1]);
		min[2] = Math.min(arr1[2], arr2[2]);
		return min;
	}

	public static double[] ceil3(final double[] array)
	{
		return ceil3(array, new double[3]);
	}

	public static double[] ceil3(final double[] array, final double[] ceil)
	{
		ceil[0] = Math.ceil(array[0]);
		ceil[1] = Math.ceil(array[1]);
		ceil[2] = Math.ceil(array[2]);
		return ceil;
	}

	public static double[] floor3(final double[] array)
	{
		return floor3(array, new double[3]);
	}

	public static double[] floor3(final double[] array, final double[] floor)
	{
		floor[0] = Math.floor(array[0]);
		floor[1] = Math.floor(array[1]);
		floor[2] = Math.floor(array[2]);
		return floor;
	}

	public static long[] asLong3(final double[] array)
	{
		return asLong3(array, new long[3]);
	}

	public static long[] asLong3(final double[] array, final long[] arrayAsLong)
	{
		arrayAsLong[0] = (long) array[0];
		arrayAsLong[1] = (long) array[1];
		arrayAsLong[2] = (long) array[2];
		return arrayAsLong;
	}

	public static int[] asInt3(final double[] array, boolean failIfNonIntegral) throws DoubleHasNonIntegralValue {
		return asInt3(array, new int[3], failIfNonIntegral);
	}

	public static int[] asInt3(final double[] array, final int[] arrayAsInt, boolean failIfNonIntegral) throws DoubleHasNonIntegralValue {
		arrayAsInt[0] = (int) array[0];
		arrayAsInt[1] = (int) array[1];
		arrayAsInt[2] = (int) array[2];

		if (failIfNonIntegral)
		{
			if (arrayAsInt[0] != array[0])
				throw new DoubleHasNonIntegralValue(array[0]);
			if (arrayAsInt[1] != array[1])
				throw new DoubleHasNonIntegralValue(array[1]);
			if (arrayAsInt[2] != array[2])
				throw new DoubleHasNonIntegralValue(array[2]);
		}

		return arrayAsInt;
	}

}
