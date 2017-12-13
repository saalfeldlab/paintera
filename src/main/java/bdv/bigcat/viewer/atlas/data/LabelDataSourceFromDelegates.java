package bdv.bigcat.viewer.atlas.data;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

public class LabelDataSourceFromDelegates< D, T > implements LabelDataSource< D, T >
{

	private final DataSource< D, T > source;

	private final FragmentSegmentAssignmentState< ? > assignment;

	public LabelDataSourceFromDelegates( final DataSource< D, T > source, final FragmentSegmentAssignmentState< ? > assignment )
	{
		super();
		this.source = source;
		this.assignment = assignment;
	}

	@Override
	public RandomAccessibleInterval< D > getDataSource( final int t, final int level )
	{
		return source.getDataSource( t, level );
	}

	@Override
	public RealRandomAccessible< D > getInterpolatedDataSource( final int t, final int level, final Interpolation method )
	{
		return source.getInterpolatedDataSource( t, level, method );
	}

	@Override
	public D getDataType()
	{
		return source.getDataType();
	}

	@Override
	public boolean isPresent( final int t )
	{
		return source.isPresent( t );
	}

	@Override
	public RandomAccessibleInterval< T > getSource( final int t, final int level )
	{
		return source.getSource( t, level );
	}

	@Override
	public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		return source.getInterpolatedSource( t, level, method );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		source.getSourceTransform( t, level, transform );
	}

	@Override
	public T getType()
	{
		return source.getType();
	}

	@Override
	public String getName()
	{
		return source.getName();
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return source.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return source.getNumMipmapLevels();
	}

	@Override
	public FragmentSegmentAssignmentState< ? > getAssignment()
	{
		return assignment;
	}

	@Override
	public int tMin()
	{
		return source.tMin();
	}

	@Override
	public int tMax()
	{
		return source.tMax();
	}

}
