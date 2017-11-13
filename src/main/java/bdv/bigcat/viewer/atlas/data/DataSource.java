package bdv.bigcat.viewer.atlas.data;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;

/**
 *
 * {@link Source} that includes a type <code>D</code> representation that is
 * used for data processing (in contrast to <code>T</code> that is used for
 * visualization).
 *
 */
public interface DataSource< D, T > extends Source< T >
{

	public RandomAccessibleInterval< D > getDataSource( int t, int level );

	public RealRandomAccessible< D > getInterpolatedDataSource( final int t, final int level, final Interpolation method );

	public D getDataType();

}
