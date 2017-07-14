package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.SourceAndConverter;
import javafx.application.Application;
import javafx.stage.Stage;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;

public class ExampleApplication extends Application
{

	public ExampleApplication()
	{
		super();
	}

	public static HashMap< Long, Atlas > activeViewers = new HashMap<>();

	public static AtomicLong index = new AtomicLong( 0 );

	public static void main( final String[] args ) throws Exception
	{
		System.out.println( "before: " + activeViewers );
		final Atlas viewer = makeViewer();
		System.out.println( "after: " + activeViewers );

		final Random rng = new Random();

		final RandomAccessibleInterval< FloatType > rai1 = createRandom( rng, 100, 200, 300 );
		final RandomAccessibleInterval< FloatType > rai2 = createRandom( rng, 50, 100, 150 );

		final FloatType t = new FloatType();
		final RandomAccessibleIntervalSource< FloatType > source1 = new RandomAccessibleIntervalSource<>( rai1, t, "source1" );
		final RandomAccessibleIntervalSource< FloatType > source2 = new RandomAccessibleIntervalSource<>( rai2, t, "source2" );

		viewer.addSource( new SourceAndConverter<>( source1, new RealARGBConverter<>( 0.0, 1.0 ) ) );
		viewer.addSource( new SourceAndConverter<>( source2, new RealARGBConverter<>( 0.0, 1.0 ) ) );


//		final List< SourceAndConverter< ? > > sacs2 = createSourceAndConverter( rng, new RealARGBConverter<>( 0.0, 1.0 ), 100, 200, 300 );
//		sacs2.forEach( viewer::addSource );
//
//		final List< SourceAndConverter< ? > > sacs1 = createSourceAndConverter( rng, new RealARGBConverter<>( 0.0, 1.0 ), 50, 100, 150 );
//		sacs1.forEach( viewer::addSource );
	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		final Atlas viewer = new Atlas();
		viewer.start( primaryStage );
		System.out.println( getParameters() );
		activeViewers.put( Long.parseLong( getParameters().getRaw().get( 0 ) ), viewer );
	}

	public static Atlas makeViewer() throws InterruptedException
	{

		synchronized ( index )
		{
			final long idx = index.get();
			final Thread t = new Thread( () -> Application.launch( ExampleApplication.class, Long.toString( idx ) ) );
			t.start();
			while ( !activeViewers.containsKey( idx ) )
				Thread.sleep( 10 );
			return activeViewers.get( idx );
		}
	}

	public static RandomAccessibleInterval< FloatType > createRandom( final Random rng, final long... size )
	{
		final Img< FloatType > rai = ArrayImgs.floats( size );

		for ( final FloatType f1 : rai )
			f1.set( rng.nextFloat() );

		return rai;
	}

//	public static List< SourceAndConverter< ? > > createSourceAndConverter( final Random rng, final Converter< FloatType, ARGBType > conv, final long... size )
//	{
//		final Img< FloatType > rai = ArrayImgs.floats( size );
//
//		for ( final FloatType f1 : rai )
//			f1.set( rng.nextFloat() );
//
//		final AffineTransform3D tf = new AffineTransform3D();
//
//		final List< SourceAndConverter< FloatType > > sacs = toSourceAndConverter( rai, conv, AxisOrder.XYZ, tf, "ok" );
//		final List< SourceAndConverter< ? > > sacsWildcard = sacs.stream().map( sac -> ( SourceAndConverter< ? > ) sac ).collect( Collectors.toList() );
//		return sacsWildcard;
//	}

//	public static < T extends RealType< T > > List< SourceAndConverter< T > > toSourceAndConverter(
//			final RandomAccessibleInterval< T > img,
//			final Converter< T, ARGBType > converter,
//			final AxisOrder axisOrder,
//			final AffineTransform3D sourceTransform,
//			final String... names )
//	{
//		final T type = Util.getTypeFromInterval( img );
//		final List< SourceAndConverter< T > > sources = new ArrayList<>();
//		final ArrayList< RandomAccessibleInterval< T > > stacks = AxisOrder.splitInputStackIntoSourceStacks( img, axisOrder );
//
//		assert names.length == stacks.size();
//
//		for ( int i = 0; i < stacks.size(); ++i )
//		{
//			final RandomAccessibleInterval< T > stack = stacks.get( i );
//			final String name = names[ i ];
//			final Source< T > s;
//			if ( stack.numDimensions() > 3 )
//				s = new RandomAccessibleIntervalSource4D<>( stack, type, sourceTransform, name );
//			else
//				s = new RandomAccessibleIntervalSource<>( stack, type, sourceTransform, name );
//			final TransformedSource< T > ts = new TransformedSource<>( s );
//			final SourceAndConverter< T > sac = new SourceAndConverter<>( ts, converter );
//			sources.add( sac );
//		}
//
//		return sources;
//	}

}