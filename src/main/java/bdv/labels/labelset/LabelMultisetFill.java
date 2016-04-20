package bdv.labels.labelset;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Set;

import gnu.trove.list.array.TLongArrayList;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;

/**
 *
 * Flood fill utility that flood fills canvas with label also comparing with
 * {@link LabelMultisetType} at the same position
 *
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 */
public class LabelMultisetFill
{

	public static interface Filler< T >
	{
		public boolean hasDifferentLabel( T value );

		public void fill( T value );
	}

	public static class LongTypeFiller< T extends IntegerType< T > > implements Filler< T >
	{
		private final long newLabel;

		public LongTypeFiller( final long newLabel )
		{
			this.newLabel = newLabel;
		}

		@Override
		public boolean hasDifferentLabel( final T value )
		{
			return value.getIntegerLong() != newLabel;
		}

		@Override
		public void fill( final T value )
		{
			value.setInteger( newLabel );
		}
	}

	public static class BitTypeFiller implements Filler< BitType >
	{

		@Override
		public boolean hasDifferentLabel( final BitType value )
		{
			// value.get() is true if has been visited, false if not, thus '!'
			return !value.get();
		}

		@Override
		public void fill( final BitType value )
		{
			value.setOne();
		}
	}

	/**
	 * Starting at seed, flood fill with label newLabel all pixels within a
	 * canvas that contain (in the sense of LabelMultisetType) the label of
	 * labels at seed.
	 *
	 * @param labels
	 *            {@link RandomAccessibleInterval} containing
	 *            {@link LabelMultisetType} for each pixel
	 * @param canvas
	 *            {@link RandomAccessibleInterval} canvas containing region to
	 *            be filled
	 * @param seed
	 *            {@link Localizable} initial seed for fill
	 * @param shape
	 *            {@link Shape} neighborhood for flood fill (e.g. 2D
	 *            4-neighborhood, 8-neighborhood or higher-dimensional
	 *            equivalents)
	 * @param filler
	 *            policy for comparing neighboring pixels (check if visited) and
	 *            fill/write into pixel
	 */
	public static < T > void fill(
			final RandomAccessible< LabelMultisetType > labels,
			final RandomAccessible< T > canvas,
			final Localizable seed,
			final Shape shape,
			final Filler< T > filler )
	{
		final RandomAccess< LabelMultisetType > labelsAccess = labels.randomAccess();
		labelsAccess.setPosition( seed );

		final Set< Multiset.Entry< Label > > entries = labelsAccess.get().entrySet();
		long seedLabel = -1;
		long seedCount = 0;
		for ( final Multiset.Entry< Label > e : entries )
		{
			final int count = e.getCount();
			if ( count > seedCount )
			{
				seedCount = count;
				seedLabel = e.getElement().id();
			}
		}

		if ( seedCount > 0 )
			fill( labels, canvas, seed, seedLabel, shape, filler );

	}


	/**
	 * Starting at seed, flood fill with label newLabel all pixels within a
	 * canvas that contain (in the sense of LabelMultisetType) the label of
	 * labels at seed.
	 *
	 * @param labels
	 *            {@link RandomAccessibleInterval} containing
	 *            {@link LabelMultisetType} for each pixel
	 * @param canvas
	 *            {@link RandomAccessibleInterval} canvas containing region to
	 *            be filled
	 * @param seed
	 *            {@link Localizable} initial seed for fill
	 * @param seedLabel
	 *            label at seed point (will be compared to
	 *            {@link LabelMultisetType} at neighboring pixels
	 * @param shape
	 *            {@link Shape} neighborhood for flood fill (e.g. 2D
	 *            4-neighborhood, 8-neighborhood or higher-dimensional
	 *            equivalents)
	 * @param min
	 *            minimum bound of labels and canvas
	 * @param max
	 *            maximum bound of labels and canvas
	 * @param filler
	 *            policy for comparing neighboring pixels (check if visited) and
	 *            fill/write into pixel
	 */
	public static < T > void fill(
			final RandomAccessible< LabelMultisetType > labels,
			final RandomAccessible< T > canvas,
			final Localizable seed,
			final long seedLabel,
			final Shape shape,
			final Filler< T > filler )
	{
		final int n = labels.numDimensions();

		final TLongArrayList[] coordinates = new TLongArrayList[ n ];
		for ( int d = 0; d < n; ++d )
		{
			coordinates[ d ] = new TLongArrayList();
			coordinates[ d ].add( seed.getLongPosition( d ) );
		}

		final RandomAccessible< Neighborhood< LabelMultisetType > > labelsNeighborhood = shape.neighborhoodsRandomAccessible( labels );
		final RandomAccessible< Neighborhood< T > > canvasNeighborhood = shape.neighborhoodsRandomAccessible( canvas );
		final RandomAccess< Neighborhood< LabelMultisetType > > labelsNeighborhoodAccess = labelsNeighborhood.randomAccess();
        final RandomAccess< Neighborhood< T > > canvasNeighborhoodAccess = canvasNeighborhood.randomAccess();

		final RandomAccess< T > canvasAccess = canvas.randomAccess();
		canvasAccess.setPosition( seed );
		filler.fill( canvasAccess.get() );

		for ( int i = 0; i < coordinates[ 0 ].size(); ++i )
		{
			for ( int d = 0; d < n; ++d )
			{
				final long pos = coordinates[ d ].get( i );
				labelsNeighborhoodAccess.setPosition( pos, d );
				canvasNeighborhoodAccess.setPosition( pos, d );
			}

			final Cursor< LabelMultisetType > labelsNeighborhoodCursor = labelsNeighborhoodAccess.get().cursor();
			final Cursor< T > canvasNeighborhoodCursor = canvasNeighborhoodAccess.get().cursor();

			while ( labelsNeighborhoodCursor.hasNext() )
			{
				final LabelMultisetType l = labelsNeighborhoodCursor.next();
				final T t = canvasNeighborhoodCursor.next();

				if ( filler.hasDifferentLabel( t ) && l.contains( seedLabel ) )
				{
					filler.fill( t );
					for ( int d = 0; d < n; ++d )
						coordinates[ d ].add( labelsNeighborhoodCursor.getLongPosition( d ) );
				}
			}
		}
	}

//    public static void main(String[] args) {
//        new ImageJ();
//        long[] dim = new long[]{100, 100};
//        ArrayImg<LongType, LongArray> img = ArrayImgs.longs(dim);
//        ArrayList<LabelMultisetType> multiSetLabelsList = new ArrayList<LabelMultisetType>();
//        for (ArrayCursor<LongType> c = img.cursor(); c.hasNext(); ) {
//            c.fwd();
//            c.get().set(1 + Math.min(Math.max(c.getLongPosition(0) - c.getLongPosition(1), -1), 1));
//            multiSetLabelsList.add(generateLabelMultisetTypeFromID(c.get().get(), 1));
//        }
//
//        ListImg<LabelMultisetType> labels = new ListImg<LabelMultisetType>(multiSetLabelsList, dim);
//
//        long fillLabel = 5;
//        ArrayImg<LongType, LongArray> canvas = ArrayImgs.longs(dim);
//        ArrayImg<BitType, LongArray> bitCanvas = ArrayImgs.bits(dim);
//        ArrayRandomAccess<LongType> canvasAccess = canvas.randomAccess();
//        ArrayRandomAccess<BitType> bitCanvasAccess = bitCanvas.randomAccess();
//        for (int i = 0; i <= dim[0] / 2; ++i) {
//            canvasAccess.setPosition(dim[0] / 4 + i, 0);
//            for (int k = 0; k < 2; ++k) {
//                canvasAccess.setPosition(dim[1] / 4 + k * dim[1] / 2, 1);
//                bitCanvasAccess.setPosition( canvasAccess );
//                canvasAccess.get().set(fillLabel);
//                bitCanvasAccess.get().set( true );
//            }
//        }
//
//        for (int i = 1; i <= dim[1] / 2; ++i) {
//            canvasAccess.setPosition(dim[1] / 4 + i, 1);
//            for (int k = 0; k < 2; ++k) {
//                canvasAccess.setPosition(dim[0] / 4 + k * dim[0] / 2, 0);
//                bitCanvasAccess.setPosition( canvasAccess );
//                canvasAccess.get().set(fillLabel);
//                bitCanvasAccess.get().set( true );
//            }
//        }
//
//        ImageJFunctions.show(canvas.copy(), "canvas-original");
//
//        ImageJFunctions.show(bitCanvas.copy(), "bit-canvas-original");
//
//        ImageJFunctions.show(img.copy(), "labels");
//
//        fill(labels, canvas, new Point(new long[]{70, 30}), new DiamondShape(1), new LongTypeFiller<LongType>( fillLabel ));
//
//        fill(labels, bitCanvas, new Point(new long[]{70, 30}), new DiamondShape(1), new BitTypeFiller() );
//
//        ImageJFunctions.show(canvas.copy(), "canvas-edited");
//
//        ImageJFunctions.show(bitCanvas.copy(), "bit-canvas-edited" );
//
//        System.out.println("Done");
//    }

	public static interface Intersect< T >
	{
		public void intersect( LabelMultisetType label, T source, T target );
	}

	public static class LongTypeIntersect< T extends IntegerType > implements Intersect< T >
	{
		private final long label;

		private final T maskLabel;

		public LongTypeIntersect( final long label, final T maskLabel )
		{
			this.label = label;
			this.maskLabel = maskLabel;
		}

		@Override
		public void intersect( final LabelMultisetType label, final T source, final T target )
		{
			if ( source.equals( maskLabel ) && label.contains( this.label ) )
				target.set( source );
		}
	}

	public static class BitTypeIntersect implements Intersect< BitType >
	{

		private final long label;

		public BitTypeIntersect( final long label )
		{
			this.label = label;
		}

		@Override
		public void intersect( final LabelMultisetType label, final BitType source, final BitType target )
		{
			target.set( source.get() && label.contains( this.label ) );
		}
	}

	public static < T > void intersect( final RandomAccessible< LabelMultisetType > labels, final RandomAccessible< T > sourceMask, final RandomAccessible< T > targetMask, final Interval regionOfInterest, final Intersect< T > intersect )
	{
		final Cursor< LabelMultisetType > labelsCursor = Views.interval( labels, regionOfInterest ).cursor();
		final Cursor< T > sourceCursor = Views.interval( sourceMask, regionOfInterest ).cursor();
		final Cursor< T > targetCursor = Views.interval( targetMask, regionOfInterest ).cursor();

		while ( labelsCursor.hasNext() )
		{
			intersect.intersect( labelsCursor.next(), sourceCursor.next(), targetCursor.next() );
		}

	}

	public static interface NeighborhoodCheckForIntersect< T >
	{
		public boolean isGoodNeighbor( LabelMultisetType label, T maskLabel );
	}

	public static class IntegerNeighborhoodCheckMaskOnly< T extends IntegerType< T > > implements NeighborhoodCheckForIntersect< T >
	{

		private final long maskLabel;

		public IntegerNeighborhoodCheckMaskOnly( final long maskLabel )
		{
			this.maskLabel = maskLabel;
		}

		@Override
		public boolean isGoodNeighbor( final LabelMultisetType label, final T maskLabel )
		{
			return maskLabel.getIntegerLong() == this.maskLabel;
		}
	}

	public static class IntegerNeighborhoodCheckLabelsOnly< T extends IntegerType< T > > implements NeighborhoodCheckForIntersect< T >
	{

		private final long label;

		public IntegerNeighborhoodCheckLabelsOnly( final long label )
		{
			this.label = label;
		}

		@Override
		public boolean isGoodNeighbor( final LabelMultisetType label, final T maskLabel )
		{
			return label.contains( this.label );
		}
	}

	public static class IntegerNeighborhoodCheckLabelsAndMask< T extends IntegerType< T > > implements NeighborhoodCheckForIntersect< T >
	{

		private final long label;

		private final long maskLabel;

		public IntegerNeighborhoodCheckLabelsAndMask( final long label, final long maskLabel )
		{
			this.label = label;
			this.maskLabel = maskLabel;
		}

		@Override
		public boolean isGoodNeighbor( final LabelMultisetType label, final T maskLabel )
		{
			return maskLabel.getIntegerLong() == this.maskLabel && label.contains( this.label );
		}
	}

	public static class IntegerNeighborhoodCheckLabelsOrMask< T extends IntegerType< T > > implements NeighborhoodCheckForIntersect< T >
	{

		private final long label;

		private final long maskLabel;

		public IntegerNeighborhoodCheckLabelsOrMask( final long label, final long maskLabel )
		{
			this.label = label;
			this.maskLabel = maskLabel;
		}

		@Override
		public boolean isGoodNeighbor( final LabelMultisetType label, final T maskLabel )
		{
			return maskLabel.getIntegerLong() == this.maskLabel || label.contains( this.label );
		}
	}

	public static class BooleanNeighborhoodCheckMaskOnly implements NeighborhoodCheckForIntersect< BitType >
	{
		@Override
		public boolean isGoodNeighbor( final LabelMultisetType label, final BitType maskLabel )
		{
			return maskLabel.get();
		}
	}

	public static class BooleanNeighborhoodCheckLabelsOnly implements NeighborhoodCheckForIntersect< BitType >
	{

		private final long label;

		public BooleanNeighborhoodCheckLabelsOnly( final long label )
		{
			this.label = label;
		}

		@Override
		public boolean isGoodNeighbor( final LabelMultisetType label, final BitType maskLabel )
		{
			return label.contains( this.label );
		}
	}

	public static class BooleanNeighborhoodCheckLabelsAndMask implements NeighborhoodCheckForIntersect< BitType >
	{

		private final long label;

		public BooleanNeighborhoodCheckLabelsAndMask( final long label )
		{
			this.label = label;
		}

		@Override
		public boolean isGoodNeighbor( final LabelMultisetType label, final BitType maskLabel )
		{
			return maskLabel.get() && label.contains( this.label );
		}
	}

	public static class BooleanNeighborhoodCheckLabelsOrMask implements NeighborhoodCheckForIntersect< BitType >
	{

		private final long label;

		public BooleanNeighborhoodCheckLabelsOrMask( final long label )
		{
			this.label = label;
		}

		@Override
		public boolean isGoodNeighbor( final LabelMultisetType label, final BitType maskLabel )
		{
			return maskLabel.get() || label.contains( this.label );
		}
	}

	public static < T > void intersect( final RandomAccessibleInterval< LabelMultisetType > labels, final RandomAccessibleInterval< T > sourceMask, final RandomAccessibleInterval< T > targetMask, final Localizable seed, final Shape shape, final Intersect< T > intersect, final NeighborhoodCheckForIntersect< T > neighborCheck )
	{
		final long[] min = new long[ labels.numDimensions() ];
		final long[] max = new long[ labels.numDimensions() ];

		labels.min( min );
		labels.max( max );

		intersect( labels, sourceMask, targetMask, seed, shape, intersect, neighborCheck, min, max );

	}

	public static < T > void intersect( final RandomAccessible< LabelMultisetType > labels, final RandomAccessible< T > sourceMask, final RandomAccessible< T > targetMask, final Localizable seed, final Shape shape, final Intersect< T > intersect, final NeighborhoodCheckForIntersect< T > neighborCheck, final long[] min, final long[] max )
	{
		final RandomAccess< LabelMultisetType > labelsAccess = labels.randomAccess();
		final RandomAccess< T > sourceAccess = sourceMask.randomAccess();
		final RandomAccess< T > targetAccess = targetMask.randomAccess();

		sourceAccess.setPosition( seed );

//        long startingCanvasLabel = canvasAccess.get().get();

		final long[] dim = new long[ seed.numDimensions() ];
		seed.localize( dim );

		final Point startingPoint = new Point( seed );
		final ArrayList< Localizable > visitedPoints = new ArrayList< Localizable >();
		// implement with long[]?
		final ArrayDeque< Localizable > openTasks = new ArrayDeque< Localizable >();
		openTasks.add( startingPoint );
		visitedPoints.add( startingPoint );

		final RandomAccessible< Neighborhood< LabelMultisetType > > labelsNeighborhood = shape.neighborhoodsRandomAccessible( labels );
		final RandomAccessible< Neighborhood< T > > sourceNeighborhood = shape.neighborhoodsRandomAccessible( sourceMask );
		final RandomAccess< Neighborhood< LabelMultisetType > > labelsNeighborhoodAccess = labelsNeighborhood.randomAccess();
		final RandomAccess< Neighborhood< T > > sourceNeighborhoodAccess = sourceNeighborhood.randomAccess();

		final Point dummyPoint = new Point( startingPoint.numDimensions() );

		while ( !openTasks.isEmpty() )
		{
			// get point that has been waiting for the longest
			final Localizable currentPoint = openTasks.remove();
			sourceAccess.setPosition( currentPoint );
			labelsAccess.setPosition( currentPoint );
			targetAccess.setPosition( currentPoint );

			intersect.intersect( labelsAccess.get(), sourceAccess.get(), targetAccess.get() );

			labelsNeighborhoodAccess.setPosition( currentPoint );
			sourceNeighborhoodAccess.setPosition( currentPoint );

			final Cursor< LabelMultisetType > labelsNeighborhoodCursor = labelsNeighborhoodAccess.get().cursor();
			final Cursor< T > sourceNeighborhoodCursor = sourceNeighborhoodAccess.get().cursor();

			while ( labelsNeighborhoodCursor.hasNext() )
			{
				labelsNeighborhoodCursor.fwd();
				sourceNeighborhoodCursor.fwd();
				// dummyPoint.setPosition( labelsNeighborhoodCursor );

				boolean isInBounds = true;
				for ( int d = 0; d < min.length; ++d )
				{
					final long currentPos = labelsNeighborhoodCursor.getLongPosition( d );
					if ( currentPos < min[ d ] || currentPos > max[ d ] )
					{
						isInBounds = false;
						break;
					}
				}
				final Point newTask = new Point( labelsNeighborhoodCursor );
				if ( isInBounds && ( !visitedPoints.contains( newTask ) ) && neighborCheck.isGoodNeighbor( labelsNeighborhoodCursor.get(), sourceNeighborhoodCursor.get() ) )
				{
					openTasks.add( newTask );
					visitedPoints.add( newTask );
				}
			}

		}
	}

	public static LabelMultisetType generateLabelMultisetTypeFromID( final long id, final int count )
	{
		final VolatileLabelMultisetArray arr = new VolatileLabelMultisetArray( 1, true );
		final long[] l = ( ( LongMappedAccessData ) arr.getListData() ).data;
		ByteUtils.putInt( count, l, 12 );
		ByteUtils.putLong( id, l, 4 );
		return new LabelMultisetType( arr );
	}

//    public static void main(String[] args) {
//
//        long[] dim = new long[]{80, 60};
//
//        long label = 10;
//
//
//        ArrayImg<LongType, LongArray> labelsLong = ArrayImgs.longs(dim);
//        ArrayList<LabelMultisetType> labelsList = new ArrayList<LabelMultisetType>();
//        for (ArrayCursor<LongType> c = labelsLong.cursor(); c.hasNext(); )
//        {
//            c.fwd();
//            c.get().set( c.getIntPosition( 0 ) > c.getIntPosition( 1 ) ? label : 0 );
//            labelsList.add( generateLabelMultisetTypeFromID( c.get().get(), 1 ) );
//        }
//
//        new ImageJ();
//        ImageJFunctions.show( labelsLong, "labels" );
//        ListImg<LabelMultisetType> labels = new ListImg<LabelMultisetType>(labelsList, dim);
//
//        ArrayImg<BitType, LongArray> source = ArrayImgs.bits(dim);
//        ArrayImg<BitType, LongArray> target = ArrayImgs.bits(dim);
//
//        ShortProcessor shortTargetProc = new ShortProcessor((int) dim[0], (int) dim[1]);
//        short[] shortTargetArray = (short[]) shortTargetProc.getPixels();
//        ArrayImg<UnsignedShortType, ShortArray> shortSource = ArrayImgs.unsignedShorts(dim);
//        ArrayImg<UnsignedShortType, ShortArray> shortTarget = ArrayImgs.unsignedShorts(shortTargetArray, dim);
//
//
//
//
//        // x, y, r
//        long[][] circles = new long[][]{
//                { 10, 10, 10 },
//                { 50, 30, 20 }
//        };
//
//        ArrayCursor<UnsignedShortType> s = shortSource.cursor();
//        for (ArrayCursor<BitType> c = source.cursor(); c.hasNext() ; )
//        {
//            c.fwd();
//            s.fwd();
//            for( int i = 0; i < circles.length; ++i )
//            {
//                long diffX = c.getIntPosition( 0 ) - circles[i][0];
//                long diffY = c.getIntPosition( 1 ) - circles[i][1];
//                if ( diffX * diffX + diffY * diffY <= circles[i][2]*circles[i][2] ) {
//                    c.get().set(true);
//                    s.get().set(1);
//                }
//            }
//        }
//
//        ImageJFunctions.show( source, "source" );
//
//        intersect( labels, source, target, source, new BitTypeIntersect( label ) );
//        ImageJFunctions.show( target.copy(), "target interval intersect" );
//        for ( BitType t : target ) t.set( false );
//
//        Point seed = new Point(new long[]{50, 30});
//        intersect( labels, source, target, seed, new DiamondShape( 1 ), new BitTypeIntersect( label ), new BooleanNeighborhoodCheckLabelsOnly( label ) );
//        ImageJFunctions.show( target.copy(), "fill intersect labels only" );
//        for ( BitType t : target ) t.set( false );
//
//        intersect( labels, source, target, seed, new DiamondShape( 1 ), new BitTypeIntersect( label ), new BooleanNeighborhoodCheckMaskOnly() );
//        ImageJFunctions.show( target.copy(), "fill intersect mask only" );
//        for ( BitType t : target ) t.set( false );
//
//        intersect( labels, source, target, seed, new DiamondShape( 1 ), new BitTypeIntersect( label ), new BooleanNeighborhoodCheckLabelsAndMask( label ) );
//        ImageJFunctions.show( target.copy(), "fill intersect both mask and labels" );
//        for ( BitType t : target ) t.set( false );
//
//
//
//        final ImagePlus shortImp = new ImagePlus("fill intersect both mask or labels", shortTargetProc);
//        shortImp.show();
//
//        Intersect<UnsignedShortType> intersect = new Intersect<UnsignedShortType>() {
//
//            long compLabel = label;
//
//            @Override
//            public void intersect(LabelMultisetType label, UnsignedShortType source, UnsignedShortType target) {
//                if (source.getIntegerLong() == 1 && label.contains(compLabel)) {
//                    try {
//                        Thread.sleep( 2 );
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    target.set( 1 );
//                    shortImp.updateAndDraw();
//                }
//
//            }
//        };
//        intersect(
//                labels,
//                shortSource,
//                shortTarget,
//                seed,
//                new DiamondShape( 1 ),
//                intersect /* new BitTypeIntersect( label ) */,
//                new IntegerNeighborhoodCheckLabelsOrMask<UnsignedShortType>( label, 1 ) );
//        for ( BitType t : target ) t.set( false );
//
//        System.out.println( "Done" );
//
//    }

}
