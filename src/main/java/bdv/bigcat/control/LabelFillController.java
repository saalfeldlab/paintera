package bdv.bigcat.control;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.label.IdPicker;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import bdv.util.Affine3DHelpers;
import bdv.viewer.ViewerPanel;
import net.imglib2.*;
import net.imglib2.Point;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.fill.Writer;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.*;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.RandomAccessiblePair;
import net.imglib2.view.Views;
import org.apache.commons.lang.math.NumberUtils;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import java.awt.Cursor;

/**
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 */
public class LabelFillController
{
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

	private final IdPicker idPicker;

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	private final double minLabelScale;

	public LabelFillController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LabelMultisetType > labels,
			final RandomAccessibleInterval< LongType > paintedLabels,
			final AffineTransform3D labelTransform,
			final FragmentSegmentAssignment assignment,
			final SelectionController selectionController,
			final Shape shape,
			final IdPicker idPicker,
			final InputTriggerConfig config )
	{
		this.viewer = viewer;
		this.labels = labels;
		this.paintedLabels = paintedLabels;
		this.labelTransform = labelTransform;
		this.assignment = assignment;
		this.selectionController = selectionController;
		this.shape = shape;
		this.idPicker = idPicker;
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "fill" );

		labelLocation = new RealPoint( 3 );

		minLabelScale = NumberUtils.min(new double[] {
				Affine3DHelpers.extractScale( labelTransform, 0 ),
				Affine3DHelpers.extractScale( labelTransform, 1 ),
				Affine3DHelpers.extractScale( labelTransform, 2 )
		} );


		new Fill( "fill", "M button1" ).register();
		new Fill2D( "fill 2D", "shift M button1" ).register();
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

	private class Fill extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public Fill( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			synchronized ( viewer )
			{
				if ( idPicker.getIdAtDisplayCoordinate( x, y ) == Label.OUTSIDE )
					return;
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				setCoordinates( x, y );
				System.out.println( "Filling " + labelLocation + " with " + selectionController.getActiveFragmentId() );

				final Point p = new Point(
						Math.round( labelLocation.getDoublePosition( 0 ) ),
						Math.round( labelLocation.getDoublePosition( 1 ) ),
						Math.round( labelLocation.getDoublePosition( 2 ) ) );

				final RandomAccess< LongType > paintAccess = Views.extendValue( paintedLabels, new LongType( Label.TRANSPARENT ) ).randomAccess();
				paintAccess.setPosition( p );
				final long seedPaint = paintAccess.get().getIntegerLong();
				final long seedFragmentLabel = getBiggestLabel( labels, p );

				final long t0 = System.currentTimeMillis();
				FloodFill.fill(
						Views.extendValue( labels, new LabelMultisetType() ),
						Views.extendValue( paintedLabels, new LongType( Label.TRANSPARENT ) ),
						p,
						new LabelMultisetType(),
						new LongType( selectionController.getActiveFragmentId() ),
						new DiamondShape( 1 ),
						new SegmentAndPaintFilter1(
								seedPaint,
								seedFragmentLabel,
								assignment ) );
				final long t1 = System.currentTimeMillis();
				System.out.println( "Filling took " + ( t1 - t0 ) + " ms" );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
				viewer.requestRepaint();
			}
		}
	}

	private class Fill2D extends SelfRegisteringBehaviour implements ClickBehaviour
	{


		public Fill2D( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			synchronized ( viewer )
			{
				if ( idPicker.getIdAtDisplayCoordinate( x, y ) == Label.OUTSIDE )
					return;
				AffineTransform3D transform = new AffineTransform3D();
				viewer.getState().getViewerTransform( transform );
				double scale = Affine3DHelpers.extractScale(transform, 0) * minLabelScale / Math.sqrt( 3 );
				System.out.println( labelTransform );
				final int xScale = (int) Math.round( x / scale );
				final int yScale = (int) Math.round( y / scale );
				final long[] initialMin = { xScale-16, yScale-16 };
				final long[] initialMax = { xScale+15, yScale+15 };
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				setCoordinates( x, y );
				System.out.println( "Filling " + labelLocation + " with " + selectionController.getActiveFragmentId() + " (2D)" );



				RealPoint rp = new RealPoint( 3 );
				transform.apply( labelLocation, rp );
				final Point p = new Point( xScale, yScale );
				final long z = (long) rp.getDoublePosition( 2 );

				System.out.println( x + " " + y + " " + p + " " + rp );

				AffineTransform3D tf = labelTransform.copy();
				tf.preConcatenate( transform );
				tf.preConcatenate( new Scale3D( 1.0 / scale, 1.0 / scale, 1.0 / scale ) );

				AffineTransform3D tfFront = tf.copy().preConcatenate(new Translation3D(0, 0, -0.5 / Math.sqrt(3)));
				AffineTransform3D tfBack = tf.copy().preConcatenate(new Translation3D(0, 0, +0.5 / Math.sqrt(3)));



				final long t0 = System.currentTimeMillis();

				GrowingStoreRandomAccessibleSingletonAccess<LongType> tmpFillFront = fillMask(tfFront, initialMin, initialMax, p);
				GrowingStoreRandomAccessibleSingletonAccess<LongType> tmpFillBack = fillMask(tfBack, initialMin, initialMax, p);

				writeMask( tmpFillFront, tfFront );
				writeMask( tmpFillBack, tfBack );


				final long t1 = System.currentTimeMillis();
				System.out.println( "Filling took " + ( t1 - t0 ) + " ms" );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
				viewer.requestRepaint();

			}
		}

		private GrowingStoreRandomAccessibleSingletonAccess< LongType > fillMask( AffineTransform3D tf, long[] initialMin, long[] initialMax, Point p )
		{

			GrowingStoreRandomAccessibleSingletonAccess<LongType> tmpFill =
					new GrowingStoreRandomAccessibleSingletonAccess<>(
							initialMin,
							initialMax,
							new GrowingStoreRandomAccessibleSingletonAccess.SimpleArrayImgFactory<>( new LongType( Label.INVALID )),
							new LongType() );

			GrowingStoreRandomAccessibleSingletonAccess<BitType> tmpFillNotVisitedYet =
					new GrowingStoreRandomAccessibleSingletonAccess<>(
							initialMin.clone(),
							initialMax.clone(),
							new GrowingStoreRandomAccessibleSingletonAccess.SimpleArrayImgFactory<>(new BitType(true)),
							new BitType() );

			RandomAccessiblePair<LongType, BitType> tmpFillPair = new RandomAccessiblePair<>(tmpFill, tmpFillNotVisitedYet);

			AffineRandomAccessible<LongType, AffineGet> transformedPaintedLabels = RealViews.affine(
					Views.interpolate(Views.extendValue(paintedLabels, new LongType(Label.TRANSPARENT)), new NearestNeighborInterpolatorFactory<>()),
					tf );
			MixedTransformView<LongType> hyperSlice = Views.hyperSlice(Views.raster(transformedPaintedLabels), 2, 0 );

			AffineRandomAccessible<LabelMultisetType, AffineGet> transformedLabels = RealViews.affine(
					Views.interpolate(Views.extendValue(labels, new LabelMultisetType()), new NearestNeighborInterpolatorFactory<>()),
					tf );
			MixedTransformView<LabelMultisetType> hyperSliceLabels = Views.hyperSlice(Views.raster(transformedLabels), 2, 0 );

			RandomAccessiblePair<LabelMultisetType, LongType> labelsPaintedLabelsPair = new RandomAccessiblePair<>(hyperSliceLabels, hyperSlice);



			RandomAccessiblePair<LabelMultisetType, LongType>.RandomAccess pairAccess = labelsPaintedLabelsPair.randomAccess();
			pairAccess.setPosition( p );
			long seedPaint = pairAccess.get().getB().getIntegerLong();
			long seedFragmentLabel = getBiggestLabel( pairAccess.getA() );

			Writer<Pair<LongType, BitType>> w = new Writer<Pair<LongType, BitType>>() {
				@Override
				public void write(Pair<LongType, BitType> source, Pair<LongType, BitType> target) {
					target.getA().set( source.getA() );
					target.getB().set( source.getB() );
				}
			};
			{

			}


			FloodFill.fill(
					labelsPaintedLabelsPair,
					tmpFillPair,
					p,
					new ValuePair<>(  new LabelMultisetType(), new LongType( selectionController.getActiveFragmentId() ) ),
					new ValuePair<>( new LongType( selectionController.getActiveFragmentId() ), new BitType( false ) ),
					new DiamondShape( 1 ),
					new SegmentAndPaintFilter2D( seedPaint, seedFragmentLabel, assignment ),
					w
			);

			return tmpFill;
		}


		private void writeMask( GrowingStoreRandomAccessibleSingletonAccess< LongType > tmpFill, AffineTransform3D tf ) {
			IntervalView<LongType> tmpFillInterval = Views.interval(tmpFill, tmpFill.getIntervalOfSizeOfStore());
			AffineRandomAccessible<LongType, AffineGet> transformedPaintedLabels = RealViews.affine(
					Views.interpolate(Views.extendValue(paintedLabels, new LongType(Label.TRANSPARENT)), new NearestNeighborInterpolatorFactory<>()),
					tf );
			MixedTransformView<LongType> hyperSlice = Views.hyperSlice(Views.raster(transformedPaintedLabels), 2, 0 );
			for( net.imglib2.Cursor<LongType> s = tmpFillInterval.cursor(), t = Views.interval( hyperSlice, tmpFillInterval ).cursor(); s.hasNext(); )
			{
				LongType label = s.next();
				t.fwd();
				if ( label.get() != Label.INVALID ) {
					t.get().set(label);
//						System.out.println( t.getLongPosition( 0 ) + " " + t.getLongPosition( 1 ) );
					// System.out.println(label.get() + " " + t.get().get());
				}
			}
		}

	}

	public static class SegmentAndPaintFilter1 implements Filter< Pair< LabelMultisetType, LongType >, Pair< LabelMultisetType, LongType > >
	{
		private final long comparison;

		private final long[] fragmentsContainedInSeedSegment;

		public SegmentAndPaintFilter1( final long seedPaint, final long seedFragmentLabel, final FragmentSegmentAssignment assignment )
		{
			this.comparison = seedPaint == Label.TRANSPARENT ? seedFragmentLabel : seedPaint;
			this.fragmentsContainedInSeedSegment = assignment.getFragments( assignment.getSegment( comparison ) );
		}

		@Override
		public boolean accept( final Pair< LabelMultisetType, LongType > current, final Pair< LabelMultisetType, LongType > reference )
		{

			final LabelMultisetType currentLabelSet = current.getA();
			final long currentPaint = current.getB().getIntegerLong();

			if ( currentPaint != Label.TRANSPARENT )
				return currentPaint == comparison && currentPaint != reference.getB().getIntegerLong();

			else
			{
				for ( final long fragment : this.fragmentsContainedInSeedSegment )
				{
					if ( currentLabelSet.contains( fragment ) )
						return true;
				}
			}

			return false;
		}
	}

	public static class SegmentAndPaintFilter2D implements Filter<
			Pair< Pair< LabelMultisetType, LongType >, Pair< LongType, BitType > >,
			Pair< Pair< LabelMultisetType, LongType >, Pair< LongType, BitType > > >
	{
		private final long comparison;

		private final long[] fragmentsContainedInSeedSegment;

		public SegmentAndPaintFilter2D( long seedPaint, long seedFragmentLabel, FragmentSegmentAssignment assignment )
		{
			this.comparison = seedPaint == Label.TRANSPARENT ? seedFragmentLabel : seedPaint;
			this.fragmentsContainedInSeedSegment = assignment.getFragments( assignment.getSegment( comparison ) );
			System.out.println( "Comparison=" + this.comparison );
		}

		@Override
		public boolean accept(
				Pair<Pair<LabelMultisetType, LongType>, Pair< LongType, BitType > > current,
				Pair<Pair<LabelMultisetType, LongType>, Pair< LongType, BitType > > reference ) {


			final Pair<LongType, BitType> currentTargetPair = current.getB();

			if ( currentTargetPair.getB().get() ) {
				Pair<LabelMultisetType, LongType> currentSourcePair = current.getA();
				// Pair<LabelMultisetType, LongType> referenceSourcePair = reference.getA();

				final LabelMultisetType currentLabelSet = currentSourcePair.getA();
				final long currentPaint = currentSourcePair.getB().getIntegerLong();


				// System.out.println(currentPaint + " " + currentSourcePair.getB().getIntegerLong());

				if (currentPaint != Label.TRANSPARENT)
					return currentPaint == comparison;

				else {
					for (long fragment : this.fragmentsContainedInSeedSegment) {
						if (currentLabelSet.contains(fragment))
							return true;
					}
				}
			}

			return false;

		}
	}

	public static class FragmentFilter implements Filter< Pair< LabelMultisetType, LongType >, Pair< LabelMultisetType, LongType > >
	{

		private final long seedLabel;

		public FragmentFilter( final long seedLabel )
		{
			this.seedLabel = seedLabel;
		}

		@Override
		public boolean accept( final Pair< LabelMultisetType, LongType > current, final Pair< LabelMultisetType, LongType > reference )
		{
			return ( current.getB().getIntegerLong() != reference.getB().getIntegerLong() ) && ( current.getA().contains( seedLabel ) );
		}

	}

	public static long getBiggestLabel( final RandomAccessible< LabelMultisetType > accessible, final Localizable position )
	{
		final RandomAccess< LabelMultisetType > access = accessible.randomAccess();
		access.setPosition( position );
		return getBiggestLabel( access.get() );
	}

	public static long getBiggestLabel( final LabelMultisetType t )
	{
		int maxCount = Integer.MIN_VALUE;
		long maxLabel = -1;
		for ( final Multiset.Entry< Label > e : t.entrySet() )
		{
			final int c = e.getCount();
			if ( c > maxCount )
			{
				maxLabel = e.getElement().id();
				maxCount = c;
			}
		}
		return maxLabel;
	}

}
