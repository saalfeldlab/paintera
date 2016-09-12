package bdv.labels.labelset;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import bdv.bigcat.label.FragmentSegmentAssignment;
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
import net.imglib2.util.Pair;
import net.imglib2.view.RandomAccessiblePair;
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

    interface FillPolicy<T>
	{
		void fill( T t );

		boolean isValidNeighbor( T t );
	}


	interface FillPolicyFactory<T>
	{
		FillPolicy<T> call(T seedLabel );
	}


    /**
     *
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
     * @param fillerFactory
     *            factory for policy for comparing neighboring pixels (check if visited) and
     *            fill/write into pixel
     */
	public static < T > void fill(
			final RandomAccessible< LabelMultisetType > labels,
			final RandomAccessible< T > canvas,
			final Localizable seed,
			final Shape shape,
			final FillPolicyFactory< Pair< LabelMultisetType, T > > fillerFactory
	)
	{
		final RandomAccessiblePair<LabelMultisetType, T> pairAccessible = new RandomAccessiblePair<>(labels, canvas);
		final RandomAccessiblePair.RandomAccess pairAccess = pairAccessible.randomAccess();
		pairAccess.setPosition( seed );
		fill( pairAccessible, seed, shape, fillerFactory.call( pairAccess.get() ) );
	}


    /**
     * @param randomAccessible {@link RandomAccessible} input (also written to)
     * @param seed
     *            {@link Localizable} initial seed for fill
     *            {@link LabelMultisetType} at neighboring pixels
     * @param shape
     *            {@link Shape} neighborhood for flood fill (e.g. 2D
     *            4-neighborhood, 8-neighborhood or higher-dimensional
     *            equivalents)
     * @param filler
     *            policy for comparing neighboring pixels (check if visited) and
     *            fill/write into pixel
     */
	public static < T > void fill(
			final RandomAccessible< T > randomAccessible,
			final Localizable seed,
			final Shape shape,
			final FillPolicy< T > filler )
	{
		final int n = randomAccessible.numDimensions();

		final TLongArrayList[] coordinates = new TLongArrayList[ n ];
		for ( int d = 0; d < n; ++d )
		{
			coordinates[ d ] = new TLongArrayList();
			coordinates[ d ].add( seed.getLongPosition( d ) );
		}

		final RandomAccessible<Neighborhood<T>> neighborhood = shape.neighborhoodsRandomAccessible(randomAccessible);
		final RandomAccess<Neighborhood<T>> neighborhoodAccess = neighborhood.randomAccess();

		final RandomAccess< T > canvasAccess = randomAccessible.randomAccess();
		canvasAccess.setPosition( seed );
		filler.fill( canvasAccess.get() );

		for ( int i = 0; i < coordinates[ 0 ].size(); ++i )
		{
			for ( int d = 0; d < n; ++d )
				neighborhoodAccess.setPosition( coordinates[ d ].get( i ), d );

			final Cursor<T> neighborhoodCursor = neighborhoodAccess.get().cursor();

			while ( neighborhoodCursor.hasNext() )
			{
				final T t = neighborhoodCursor.next();
				if ( filler.isValidNeighbor( t ) )
				{
					filler.fill( t );
					for ( int d = 0; d < n; ++d )
						coordinates[ d ].add( neighborhoodCursor.getLongPosition( d ) );
				}
			}
		}
	}


    public static class IntegerTypeFillPolicyFragments< T extends IntegerType< T > > implements FillPolicy< Pair< LabelMultisetType, T > >
    {

        public static class Factory<U extends IntegerType< U > > implements FillPolicyFactory< Pair< LabelMultisetType, U > >
        {

            private final long newLabel;

            public Factory( final long newLabel ) {
                this.newLabel = newLabel;
            }

            @Override
            public IntegerTypeFillPolicyFragments< U > call(final Pair<LabelMultisetType, U> seedPair ) {
                return new IntegerTypeFillPolicyFragments<>( this.newLabel, getLabelWithMostCounts( seedPair.getA() ) );
            }
        }

        private final long newLabel;
        private final long seedLabel;

        public IntegerTypeFillPolicyFragments(final long newLabel, final long seedLabel) {
            this.newLabel = newLabel;
            this.seedLabel = seedLabel;
        }

        @Override
        public void fill(final Pair< LabelMultisetType, T > p) {
            p.getB().setInteger( newLabel );
        }

        @Override
        public boolean isValidNeighbor(final Pair< LabelMultisetType, T > p) {
            final LabelMultisetType l = p.getA();
            final T t = p.getB();
            return t.getIntegerLong() != this.newLabel && l.contains( this.seedLabel );
        }
    }


    public static class IntegerTypeFillPolicyFragmentsConsiderBackgroundAndCanvas< T extends IntegerType< T > > implements FillPolicy< Pair< LabelMultisetType, T > >
    {

        public static class Factory<U extends IntegerType< U > > implements FillPolicyFactory< Pair< LabelMultisetType, U > >
        {

            private final long newLabel;

            public Factory( final long newLabel ) {
                this.newLabel = newLabel;
            }

            @Override
            public IntegerTypeFillPolicyFragmentsConsiderBackgroundAndCanvas< U > call(final Pair<LabelMultisetType, U> seedPair ) {
                return new IntegerTypeFillPolicyFragmentsConsiderBackgroundAndCanvas<>( this.newLabel, getLabelWithMostCounts( seedPair.getA() ) );
            }
        }

        private final long newLabel;
        private final long seedLabel;

        public IntegerTypeFillPolicyFragmentsConsiderBackgroundAndCanvas(final long newLabel, final long seedLabel) {
            this.newLabel = newLabel;
            this.seedLabel = seedLabel;
        }

        @Override
        public void fill(final Pair< LabelMultisetType, T > p) {
            p.getB().setInteger( newLabel );
        }

        @Override
        public boolean isValidNeighbor(final Pair< LabelMultisetType, T > p) {
            final LabelMultisetType l = p.getA();
            final long t = p.getB().getIntegerLong();
            return ( ( t == seedLabel ) || ( ( t == Label.TRANSPARENT ) && l.contains( seedLabel ) ) );
        }
    }


    // if ( ( b == seedLabel ) || ( ( b == TRANSPARENT_LABEL ) && filler.anyLabelInMultisetIsPartOfSeedSegment( l.getA() ) ) ) // l.getA().contains( seedLabel ) ) )
    public static abstract class AbstractIntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas< T extends IntegerType< T> >
            implements FillPolicy< Pair< LabelMultisetType, T > >
    {

        private final long newLabel;
        private final long segmentSeedLabel;

        public AbstractIntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas(final long newLabel, final long segmentSeedLabel) {
            this.newLabel = newLabel;
            this.segmentSeedLabel = segmentSeedLabel;
        }

        @Override
        public void fill(final Pair< LabelMultisetType, T > p) {
            p.getB().setInteger( newLabel );
        }

        @Override
        public boolean isValidNeighbor(final Pair< LabelMultisetType, T > p) {
            final LabelMultisetType l = p.getA();
            final long t = p.getB().getIntegerLong();
            return ( ( t == segmentSeedLabel ) || ( ( t == Label.TRANSPARENT ) ) &&  anyLabelInMultisetIsPartOfSeedSegment( l ) );
//            return t.getIntegerLong() != this.newLabel && l.contains( this.seedLabel );
        }

        protected abstract boolean anyLabelInMultisetIsPartOfSeedSegment(LabelMultisetType label);
    }


    /**
     * helper function to get label with most counts in multiset
     * @param labels {@link LabelMultisetType}
     * @return label with most counts (need not be unique)
     */
    public static long getLabelWithMostCounts(
            final LabelMultisetType labels
    )
    {
        long seedLabel = -1;
        long seedCount = -1;

        for ( final Multiset.Entry<Label> e : labels.entrySet() ) {//seedPair.getA().entrySet() ) {
            final int count = e.getCount();
            if ( count > seedCount )
            {
                seedLabel = e.getElement().id();
                seedCount = count;
            }
        }
        return seedLabel;
    }


    public static class IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas1< T extends IntegerType< T > >
            extends AbstractIntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas< T >
    {

        private final long[] fragmentsContainedInSegment;

        public IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas1(
                final long newLabel,
                final Long segmentSeedLabel,
                final FragmentSegmentAssignment assignment
        ) {
            super( newLabel, segmentSeedLabel );
            this.fragmentsContainedInSegment = assignment.getFragments( segmentSeedLabel );
        }

        public static class Factory<U extends IntegerType< U > > implements FillPolicyFactory< Pair< LabelMultisetType, U > >
        {

            private final long newLabel;
            private final FragmentSegmentAssignment assignment;

            public Factory( final long newLabel, final FragmentSegmentAssignment assignment ) {
                this.newLabel = newLabel;
                this.assignment = assignment;
            }

            @Override
            public IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas1< U > call(final Pair<LabelMultisetType, U> seedPair ) {
                final long seedLabel = getLabelWithMostCounts( seedPair.getA() );
                return new IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas1<>( this.newLabel, assignment.getSegment( seedLabel ), assignment );
            }
        }

        @Override
        protected boolean anyLabelInMultisetIsPartOfSeedSegment(final LabelMultisetType label) {
            for ( final long id : this.fragmentsContainedInSegment )
                if ( label.contains( id ) )
                    return true;
            return false;
        }
    }


    public static class IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas2< T extends IntegerType< T > >
            extends AbstractIntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas< T >
    {

        private final long[] fragmentsContainedInSegment;

        public IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas2(
                final long newLabel,
                final Long segmentSeedLabel,
                final FragmentSegmentAssignment assignment
        ) {
            super( newLabel, segmentSeedLabel );
            this.fragmentsContainedInSegment = assignment.getFragments( segmentSeedLabel ).clone();
            Arrays.sort( this.fragmentsContainedInSegment );
        }

        public static class Factory<U extends IntegerType< U > > implements FillPolicyFactory< Pair< LabelMultisetType, U > >
        {

            private final long newLabel;
            private final FragmentSegmentAssignment assignment;

            public Factory( final long newLabel, final FragmentSegmentAssignment assignment ) {
                this.newLabel = newLabel;
                this.assignment = assignment;
            }

            @Override
            public IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas2< U > call(final Pair<LabelMultisetType, U> seedPair ) {
                final long seedLabel = getLabelWithMostCounts( seedPair.getA() );
                return new IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas2<>( this.newLabel, assignment.getSegment( seedLabel ), assignment );
            }
        }

        @Override
        protected boolean anyLabelInMultisetIsPartOfSeedSegment(final LabelMultisetType label) {
            for ( final Multiset.Entry<Label> l : label.entrySet())
            {
                if ( Arrays.binarySearch( this.fragmentsContainedInSegment, l.getElement().id() ) >= 0 )
                    return true;
            }
            return false;
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
//        fill(labels, canvas, new Point(new long[]{70, 30}), new DiamondShape(1), new IntegerTypeFillerFragments<LongType>( fillLabel ));
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
