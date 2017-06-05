package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import bdv.cache.CacheControl;
import bdv.tools.transformation.TransformedSource;
import bdv.util.AxisOrder;
import bdv.util.RandomAccessibleIntervalSource;
import bdv.util.RandomAccessibleIntervalSource4D;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerFrame;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public class Dummy
{

	public static void main( final String[] args )
	{
		final Random rng = new Random();

		final List< SourceAndConverter< ? > > sacs2 = createSourceAndConverter( rng, new RealARGBConverter<>( 0.0, 1.0 ), 100, 200, 300 );

		final ViewerFrame frame = new ViewerFrame( sacs2, 1, new CacheControl.Dummy() );

		frame.setVisible( true );
//		sacs2.forEach( frame.getViewerPanel()::addSource );

//		final List< SourceAndConverter< ? > > sacs1 = createSourceAndConverter( rng, new RealARGBConverter<>( 0.0, 1.0 ), 50, 100, 150 );
//		sacs1.forEach( frame.getViewerPanel()::addSource );

	}

	public static List< SourceAndConverter< ? > > createSourceAndConverter( final Random rng, final Converter< FloatType, ARGBType > conv, final long... size )
	{
		final Img< FloatType > rai = ArrayImgs.floats( size );

		for ( final FloatType f1 : rai )
			f1.set( rng.nextFloat() );

		final AffineTransform3D tf = new AffineTransform3D();

		final List< SourceAndConverter< FloatType > > sacs = toSourceAndConverter( rai, conv, AxisOrder.XYZ, tf, "ok" );
		final List< SourceAndConverter< ? > > sacsWildcard = sacs.stream().map( sac -> ( SourceAndConverter< ? > ) sac ).collect( Collectors.toList() );
		return sacsWildcard;
	}

	public static < T extends RealType< T > > List< SourceAndConverter< T > > toSourceAndConverter(
			final RandomAccessibleInterval< T > img,
			final Converter< T, ARGBType > converter,
			final AxisOrder axisOrder,
			final AffineTransform3D sourceTransform,
			final String... names )
	{
		final T type = Util.getTypeFromInterval( img );
		final List< SourceAndConverter< T > > sources = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< T > > stacks = AxisOrder.splitInputStackIntoSourceStacks( img, axisOrder );

		assert names.length == stacks.size();

		for ( int i = 0; i < stacks.size(); ++i )
		{
			final RandomAccessibleInterval< T > stack = stacks.get( i );
			final String name = names[ i ];
			final Source< T > s;
			if ( stack.numDimensions() > 3 )
				s = new RandomAccessibleIntervalSource4D<>( stack, type, sourceTransform, name );
			else
				s = new RandomAccessibleIntervalSource<>( stack, type, sourceTransform, name );
			final TransformedSource< T > ts = new TransformedSource<>( s );
			final SourceAndConverter< T > sac = new SourceAndConverter<>( ts, converter );
			sources.add( sac );
		}

		return sources;
	}

}
