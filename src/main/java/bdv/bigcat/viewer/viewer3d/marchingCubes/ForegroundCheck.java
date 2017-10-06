package bdv.bigcat.viewer.viewer3d.marchingCubes;

/**
 *
 * @author Philipp Hanslovsky
 *
 *         An object <code>T t</code> is considered part of the foreground if
 *         the least significant bit of the returned value is 1:
 *
 *         <code>( test( t ) & 1 ) == 1 //foreground</code>
 *         <code>( test( t ) & 1 ) == 0 //background</code>
 *
 * @param <T>
 */
public interface ForegroundCheck< T >
{

	/**
	 *
	 * @param t
	 *            Object to be foreground-tested
	 * @return <code>int</code> whose least significant bit indicates whether or
	 *         not <code>t</code> is part of the foreground:
	 *
	 *         <code>( test( t ) & 1 ) == 1 //foreground</code>
	 *         <code>( test( t ) & 1 ) == 0 //background</code>
	 *
	 */
	public int test( T t );

}
