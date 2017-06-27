package bdv.bigcat.viewer.source;

import bdv.ViewerSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileRealType;
import net.imglib2.util.Util;

public class RandomAccessibleIntervalSource< T extends RealType< T > > implements Source< T, VolatileRealType< T > >
{

	private final RandomAccessibleInterval< T > source;

	private final VolatileRealType< T > vt;

	public RandomAccessibleIntervalSource( final RandomAccessibleInterval< T > source )
	{
		super();
		this.source = source;
		this.vt = new VolatileRealType< >( Util.getTypeFromInterval( source ) );
	}

	@Override
	public ViewerSetupImgLoader< T, VolatileRealType< T > > loader()
	{
		return new ViewerSetupImgLoader< T, VolatileRealType< T > >()
		{

			@Override
			public RandomAccessibleInterval< T > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
			{
				return source;
			}

			@Override
			public double[][] getMipmapResolutions()
			{
				return new double[][] { { 1.0, 1.0, 1.0 } };
			}

			@Override
			public AffineTransform3D[] getMipmapTransforms()
			{
				return new AffineTransform3D[] { new AffineTransform3D() };
			}

			@Override
			public int numMipmapLevels()
			{
				return 1;
			}

			@Override
			public RandomAccessibleInterval< T > getImage( final int timepointId, final ImgLoaderHint... hints )
			{
				return source;
			}

			@Override
			public T getImageType()
			{
				return Util.getTypeFromInterval( source );
			}

			@Override
			public RandomAccessibleInterval< VolatileRealType< T > > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
			{
				return Converters.convert( source, ( s, t ) -> {
					t.setValid( true );
					t.setReal( s.getRealDouble() );
				}, getVolatileImageType() );
			}

			@Override
			public VolatileRealType< T > getVolatileImageType()
			{
				return vt.createVariable();
			}
		};


	}

	@Override
	public String name()
	{
		return "RAI source " + source;
	}

}
