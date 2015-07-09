package bdv.util;



/**
 * Generate a stream of `random' saturated RGB colors with all colors being
 * maximally distinct from each other.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class ColorStream
{
	final static protected double goldenRatio = 1.0 / ( 0.5 * Math.sqrt( 5 ) + 0.5 );
	final static protected double[] rs = new double[]{ 1, 1, 0, 0, 0, 1, 1 };
	final static protected double[] gs = new double[]{ 0, 1, 1, 1, 0, 0, 0 };
	final static protected double[] bs = new double[]{ 0, 0, 0, 1, 1, 1, 0 };

	static long i = -1;

	final static protected int interpolate( final double[] xs, final int k, final int l, final double u, final double v )
	{
		return ( int )( ( v * xs[ k ] + u * xs[ l ] ) * 255.0 + 0.5 );
	}

	final static protected int argb( final int r, final int g, final int b )
	{
		return ( ( ( r << 8 ) | g ) << 8 ) | b | 0xff000000;
	}

	final static public int get( final long index )
	{
		double x = goldenRatio * index;
		x -= ( long )Math.floor( x );
		x *= 6.0;
		final int k = ( int )x;
		final int l = k + 1;
		final double u = x - k;
		final double v = 1.0 - u;

		final int r = interpolate( rs, k, l, u, v );
		final int g = interpolate( gs, k, l, u, v );
		final int b = interpolate( bs, k, l, u, v );

		return argb( r, g, b );
	}

	final static public int next()
	{
		return get( ++i );
	}
}
