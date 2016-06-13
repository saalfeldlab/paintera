package bdv.bigcat.control;

import static bdv.bigcat.control.LabelFillController.getBiggestLabel;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.viewer.ViewerPanel;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.RandomAccessiblePair;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 *
 * TODO consider removing
 */
public class LabelRestrictToSegmentController
{

	private final static int CLEANUP_THRESHOLD = ( int ) 1e5;

	// current work around: fill intersect with dummy color, then fill dummy
	// color with initial color
	private final static int DUMMY_PAINT = -2;

	public static final Filter<
            Pair< Pair< LabelMultisetType, LongType >, LongType >,
            Pair< Pair< LabelMultisetType, LongType >, LongType > > LABEL_FILTER =
			( current, reference ) -> current.getA().getB().getIntegerLong() == reference.getA().getB().getIntegerLong();

	public static class LabelFilter< T extends IntegerType< T > >
			implements Filter< Pair< Pair< LabelMultisetType, T >, T >, Pair< Pair< LabelMultisetType, T >, T > >
	{

//        private final long newLabel;

//        public LabelFilter(long newLabel) {
//            this.newLabel = newLabel;
//        }

		@Override
		public boolean accept( final Pair< Pair< LabelMultisetType, T >, T > current, final Pair< Pair< LabelMultisetType, T >, T > reference )
		{
			final long currentPaint = current.getA().getB().getIntegerLong();
			return currentPaint == reference.getA().getB().getIntegerLong();
		}
	}

	public static class WriteTransparentIfDifferentSegment< T extends IntegerType< T > >
			implements Converter< Pair< LabelMultisetType, T >, T >
	{

		private final long TRANSPARENT = Label.TRANSPARENT;

		private final long[] fragmentsInSegment;

		private final long oldPaint;

		private final long newPaint;

		public WriteTransparentIfDifferentSegment( final long[] fragmentsInSegment, final long oldPaint, final long newPaint )
		{
			this.fragmentsInSegment = fragmentsInSegment;
			this.oldPaint = oldPaint;
			this.newPaint = newPaint;
		}

		@Override
		public void convert( final Pair< LabelMultisetType, T > source, final T target )
		{
			final LabelMultisetType labelMultiset = source.getA();
			// comparison to oldPaint obsolete if guaranteed to be inside
			// non-transparent?
			if ( source.getB().getIntegerLong() == oldPaint )
			{
				boolean isInSameSegment = false;
				for ( final long fragment : fragmentsInSegment )
				{
					isInSameSegment = labelMultiset.contains( fragment );
					if ( isInSameSegment )
						break;
				}
				target.setInteger( isInSameSegment ? newPaint : TRANSPARENT );
			}
		}

	}

	final protected ViewerPanel viewer;

	final protected RandomAccessibleInterval< LabelMultisetType > labels;

	final protected RandomAccessibleInterval< LongType > paintedLabels;

	final protected AffineTransform3D labelTransform;

	final protected FragmentSegmentAssignment assignment;

	final protected SelectionController selectionController;

	final protected RealPoint labelLocation;

	final protected Shape shape;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();

	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();

	private final InputTriggerAdder inputAdder;

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	public LabelRestrictToSegmentController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LabelMultisetType > labels,
			final RandomAccessibleInterval< LongType > paintedLabels,
			final AffineTransform3D labelTransform,
			final FragmentSegmentAssignment assignment,
			final SelectionController selectionController,
			final Shape shape,
			final InputTriggerConfig config )
	{
		this.viewer = viewer;
		this.labels = labels;
		this.paintedLabels = paintedLabels;
		this.labelTransform = labelTransform;
		this.assignment = assignment;
		this.selectionController = selectionController;
		this.shape = shape;
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "restrict" );

		labelLocation = new RealPoint( 3 );

		new Intersect( "restrict", "shift R button1" ).register();
	}

	private void setCoordinates( final int x, final int y )
	{
		labelLocation.setPosition( x, 0 );
		labelLocation.setPosition( y, 1 );
		labelLocation.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelLocation );

		labelTransform.applyInverse( labelLocation, labelLocation );
	}

	private abstract class SelfRegisteringBehaviour implements Behaviour
	{
		private final String name;

		private final String[] defaultTriggers;

		protected String getName()
		{
			return name;
		}

		public SelfRegisteringBehaviour( final String name, final String... defaultTriggers )
		{
			this.name = name;
			this.defaultTriggers = defaultTriggers;
		}

		public void register()
		{
			behaviourMap.put( name, this );
			inputAdder.put( name, defaultTriggers );
		}
	}

	private class Intersect extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public Intersect( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			synchronized ( viewer )
			{
				viewer.setCursor( java.awt.Cursor.getPredefinedCursor( java.awt.Cursor.WAIT_CURSOR ) );
				setCoordinates( x, y );
				System.out.println( "Intersecting " + labelLocation + " with " + selectionController.getActiveFragmentId() );

				final Point p = new Point(
						Math.round( labelLocation.getDoublePosition( 0 ) ),
						Math.round( labelLocation.getDoublePosition( 1 ) ),
						Math.round( labelLocation.getDoublePosition( 2 ) ) );

				final RandomAccess< LongType > paintAccess = paintedLabels.randomAccess();
				paintAccess.setPosition( p );
				final long seedPaint = paintAccess.get().getIntegerLong();

                if ( seedPaint != Label.TRANSPARENT ) {
                    final long seedFragmentLabel = getBiggestLabel(labels, p);
                    final long seedSegmentLabel = assignment.getSegment(seedFragmentLabel);
                    final long[] fragmentsInSeedSegment = assignment.getFragments(seedSegmentLabel);

                    final long t0 = System.currentTimeMillis();
                    // current work around: fill intersect with dummy color, then
                    // fill dummy color with initial color
                    FloodFill.fill(
                            Views.extendValue(labels, new LabelMultisetType()),
                            Views.extendValue(paintedLabels, new LongType(Label.TRANSPARENT)),
                            p,
                            new LabelMultisetType(),
                            new LongType(DUMMY_PAINT),
                            new DiamondShape(1),
                            new LabelFillController.SegmentAndPaintFilter1(
                                    seedPaint,
                                    seedFragmentLabel,
                                    assignment));
                    final long t1 = System.currentTimeMillis();
                    // current work around: fill intersect with dummy color, then
                    // fill dummy color with initial color
                    intersect(
                            Views.extendValue(labels, new LabelMultisetType()),
                            Views.extendValue(paintedLabels, new LongType(Label.TRANSPARENT)),
                            Views.extendValue(paintedLabels, new LongType(Label.TRANSPARENT)),
                            new DiamondShape(1),
                            p,
                            new ValuePair<>(new ValuePair<>(new LabelMultisetType(), new LongType(DUMMY_PAINT)), new LongType(DUMMY_PAINT)),
                            LABEL_FILTER,
                            new WriteTransparentIfDifferentSegment<>(fragmentsInSeedSegment, DUMMY_PAINT, seedPaint) // to
                            // proper
                            // newPaint
                    );
                    System.out.println( "Intersecting took " + ( t1 - t0 ) + " ms" );
                }

                viewer.setCursor( java.awt.Cursor.getPredefinedCursor( java.awt.Cursor.DEFAULT_CURSOR ) );
                viewer.requestRepaint();
			}
		}
	}

	public static < T, U, V > void intersect(
			final RandomAccessible< T > source1,
			final RandomAccessible< U > source2,
			final RandomAccessible< V > target,
			final Shape shape,
			final Localizable seed,
			final Filter< Pair< Pair< T, U >, V >, Pair< Pair< T, U >, V > > filter,
			final Converter< Pair< T, U >, V > writer )
	{
		final RandomAccessiblePair.RandomAccess access =
				new RandomAccessiblePair< >( new RandomAccessiblePair< >( source1, source2 ), target ).randomAccess();
		access.setPosition( seed );
		intersect( source1, source2, target, shape, seed, access.get().copy(), filter, writer );
	}

	public static < T, U, V > void intersect(
			final RandomAccessible< T > source1,
			final RandomAccessible< U > source2,
			final RandomAccessible< V > target,
			final Shape shape,
			final Localizable seed,
			final Pair< Pair< T, U >, V > reference,
			final Filter< Pair< Pair< T, U >, V >, Pair< Pair< T, U >, V > > filter,
			final Converter< Pair< T, U >, V > writer )
	{

		final int n = source1.numDimensions();

		final RandomAccessiblePair< T, U > sourcesPair = new RandomAccessiblePair< >( source1, source2 );
		final RandomAccessiblePair< Pair< T, U >, V > sourcesTargetPair = new RandomAccessiblePair< >( sourcesPair, target );

		final RandomAccessible< Neighborhood< Pair< Pair< T, U >, V > > > neighborhood = shape.neighborhoodsRandomAccessible( sourcesTargetPair );
		final RandomAccess< Neighborhood< Pair< Pair< T, U >, V > > > neighborhoodAccess = neighborhood.randomAccess();

		final TLongList[] coordinates = new TLongList[ n ];
		for ( int d = 0; d < n; ++d )
		{
			coordinates[ d ] = new TLongArrayList();
			coordinates[ d ].add( seed.getLongPosition( d ) );
		}

		for ( int i = 0; i < coordinates[ 0 ].size(); ++i )
		{

			// Clean-up after filling CLEANUP_THRESHOLD many pixels.
			if ( i > CLEANUP_THRESHOLD )
			{
				for ( int d = 0; d < coordinates.length; ++d )
				{
					final TLongList c = coordinates[ d ];
					coordinates[ d ] = c.subList( i, c.size() );
				}
				i = 0;
			}

			for ( int d = 0; d < n; ++d )
				neighborhoodAccess.setPosition( coordinates[ d ].get( i ), d );

			final Cursor< Pair< Pair< T, U >, V > > neighborhoodCursor = neighborhoodAccess.get().cursor();

			while ( neighborhoodCursor.hasNext() )
			{
				final Pair< Pair< T, U >, V > p = neighborhoodCursor.next();
				if ( filter.accept( p, reference ) )
				{
					writer.convert( p.getA(), p.getB() );
					for ( int d = 0; d < n; ++d )
						coordinates[ d ].add( neighborhoodCursor.getLongPosition( d ) );
				}
			}

		}

	}

	// why even use this? can't just use ConvertedRandomAccessibleInterval? and
	// copy if necessary? - works only for V extends Type<V>
	public static < T, U, V > void intersect(
			final RandomAccessibleInterval< T > source1,
			final RandomAccessibleInterval< U > source2,
			final RandomAccessibleInterval< V > target,
//			Filter< T, U > filter, // can be handled in writer? only writer: requires write for every pixel but simpler interface
			final Converter< Pair< T, U >, V > writer )
	{
		final RandomAccessiblePair< T, U > sourcesPair = new RandomAccessiblePair< >( source1, source2 );
		final RandomAccessiblePair< Pair< T, U >, V > sourcesTargetPair = new RandomAccessiblePair< >( sourcesPair, target );

		for ( final Pair< Pair< T, U >, V > entry : Views.interval( sourcesTargetPair, target ) )
		{
			final Pair< T, U > sources = entry.getA();
			writer.convert( sources, entry.getB() );
		}
	}

}
