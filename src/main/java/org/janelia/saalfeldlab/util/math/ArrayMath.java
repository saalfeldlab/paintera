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

}
