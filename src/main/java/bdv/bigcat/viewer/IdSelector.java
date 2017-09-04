package bdv.bigcat.viewer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.stream.Collectors;

import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.util.AbstractNamedBehaviour;

import bdv.bigcat.viewer.atlas.mode.HandleMultipleIds;
import bdv.bigcat.viewer.state.FragmentSegmentAssignment;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class IdSelector
{

	private final ViewerPanel viewer;

	private final HashMap< Source< ? >, Function< Object, long[] > > toIdConverters;

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< Source< ? >, Source< ? > > dataSources;

	public IdSelector(
			final ViewerPanel viewer,
			final HashMap< Source< ? >, Function< Object, long[] > > toIdConverters,
			final HashMap< Source< ? >, SelectedIds > selectedIds,
			final HashMap< Source< ? >, Source< ? > > dataSources )
	{
		super();
		this.viewer = viewer;
		this.toIdConverters = toIdConverters;
		this.selectedIds = selectedIds;
		this.dataSources = dataSources;
	}

	public SelectSingle selectSingle( final String name, final HandleMultipleIds handleMultipleEntries )
	{
		return new SelectSingle( name, handleMultipleEntries );
	}

	public Append append( final String name, final HandleMultipleIds handleMultipleEntries )
	{
		return new Append( name, handleMultipleEntries );
	}

//	public MergeFragments merge( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
//	{
//		return new MergeFragments( assignments );
//	}

	public MergeSegments merge( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
	{
		return new MergeSegments( assignments );
	}

	public DetachFragment detach( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
	{
		return new DetachFragment( assignments );
	}

	private abstract class Select extends AbstractNamedBehaviour implements ClickBehaviour
	{

		public Select( final String name )
		{
			super( name );
		}

		@Override
		public void click( final int x, final int y )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( toIdConverters.containsKey( source ) && selectedIds.containsKey( source ) && dataSources.containsKey( source ) )
			{

				final Source< ? > dataSource = dataSources.get( source );
				synchronized ( viewer )
				{
					final AffineTransform3D affine = new AffineTransform3D();
					final ViewerState state = viewer.getState();
					state.getViewerTransform( affine );
					final int level = state.getBestMipMapLevel( affine, state.getSources().stream().map( src -> src.getSpimSource() ).collect( Collectors.toList() ).indexOf( source ) );
					dataSource.getSourceTransform( 0, level, affine );
					final RealTransformRealRandomAccessible< ?, InverseRealTransform >.RealTransformRealRandomAccess access = RealViews.transformReal( dataSource.getInterpolatedSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine ).realRandomAccess();
					viewer.getMouseCoordinates( access );
					access.setPosition( 0l, 2 );
					viewer.displayToGlobalCoordinates( access );
					final Object val = access.get();
					final long[] id = toIdConverters.get( source ).apply( val );
					actOn( id, selectedIds.get( source ) );
				}
			}
		}

		protected abstract void actOn( final long[] id, SelectedIds selectedIds );
	}

	private class SelectSingle extends Select
	{

		private final HandleMultipleIds handleMultipleEntries;

		public SelectSingle( final String name, final HandleMultipleIds handleMultipleEntries )
		{
			super( name );
			this.handleMultipleEntries = handleMultipleEntries;
		}

		@Override
		protected void actOn( final long[] ids, final SelectedIds selectedIds )
		{
			switch ( ids.length )
			{
			case 0:
				return;
			case 1:
				final long id = ids[ 0 ];
				if ( selectedIds.isOnlyActiveId( id ) )
					selectedIds.deactivate( id );
				else
					selectedIds.activate( id );
				break;
			default:
				final boolean[] isActive = new boolean[ ids.length ];
				final boolean requiresAction = handleMultipleEntries.handle( ids, selectedIds, isActive );
				if ( requiresAction )
				{
					selectedIds.deactivateAll();
					for ( int i = 0; i < ids.length; ++i )
					{
						System.out.println( "ACTIVATE? " + isActive[ i ] + ids[ i ] );
						if ( isActive[ i ] )
							selectedIds.activateAlso( ids[ i ] );
						else
							selectedIds.deactivate( ids[ i ] );
					}
				}
				break;
			}
		}
	}

	private class Append extends Select
	{

		private final HandleMultipleIds handleMultipleEntries;

		public Append( final String name, final HandleMultipleIds handleMultipleEntries )
		{
			super( name );
			this.handleMultipleEntries = handleMultipleEntries;
		}

		@Override
		protected void actOn( final long[] ids, final SelectedIds selectedIds )
		{
			switch ( ids.length )
			{
			case 0:
				return;
			case 1:
				final long id = ids[ 0 ];
				if ( selectedIds.isActive( id ) )
					selectedIds.deactivate( id );
				else
					selectedIds.activateAlso( id );
				break;
			default:
				final boolean[] isActive = new boolean[ ids.length ];
				final boolean requiresAction = handleMultipleEntries.handle( ids, selectedIds, isActive );
				if ( requiresAction )
					for ( int i = 0; i < ids.length; ++i )
						if ( isActive[ i ] )
							selectedIds.activateAlso( ids[ i ] );
						else
							selectedIds.deactivate( ids[ i ] );
				break;
			}
		}

	}

	private class MergeSegments extends AbstractNamedBehaviour implements ClickBehaviour
	{

		private final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments;

		public MergeSegments( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
		{
			super( "merge segments" );
			this.assignments = assignments;
		}

		@Override
		public void click( final int x, final int y )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( toIdConverters.containsKey( source ) && selectedIds.containsKey( source ) && dataSources.containsKey( source ) && assignments.containsKey( source ) )
				synchronized ( viewer )
				{
					final FragmentSegmentAssignment assignment = assignments.get( source );

					final long[] selIds = selectedIds.get( source ).getActiveIds();

					if ( selIds.length < 1 )
						return;

					final long selectedId = assignment.getSegment( selIds[ 0 ] );

					for ( int i = 1; i < selIds.length; ++i )
						if ( assignment.getSegment( selIds[ i ] ) != selectedId )
						{
							System.out.println( "Ambiguity: Selected multiple active segments -- will not apply merge!" );
							return;
						}

					final Source< ? > dataSource = dataSources.get( source );

					final AffineTransform3D viewerTransform = new AffineTransform3D();
					final AffineTransform3D affine = new AffineTransform3D();
					final ViewerState state = viewer.getState();
					state.getViewerTransform( viewerTransform );
					final int level = state.getBestMipMapLevel( viewerTransform, state.getSources().stream().map( src -> src.getSpimSource() ).collect( Collectors.toList() ).indexOf( source ) );
					dataSource.getSourceTransform( 0, level, affine );
					final RealRandomAccessible< ? > interpolatedSource = dataSource.getInterpolatedSource( 0, level, Interpolation.NEARESTNEIGHBOR );
					final RealTransformRealRandomAccessible< ?, InverseRealTransform > transformedSource = RealViews.transformReal( interpolatedSource, affine );
					final RealRandomAccess< ? > access = transformedSource.realRandomAccess();
					viewer.getMouseCoordinates( access );
					access.setPosition( 0l, 2 );
					viewer.displayToGlobalCoordinates( access );
					final Object val = access.get();
					final Function< Object, long[] > toIdConverter = toIdConverters.get( source );
					final long[] ids = toIdConverter.apply( val );

					final TLongHashSet segments = new TLongHashSet();
					Arrays.stream( ids ).map( assignment::getSegment ).forEach( segments::add );
					Arrays.stream( selIds ).map( assignment::getSegment ).forEach( segments::add );

					final int w = viewer.getWidth();
					final int h = viewer.getHeight();
					final IntervalView< ? > screenLabels =
							Views.interval(
									Views.hyperSlice(
											RealViews.affine( transformedSource, viewerTransform ), 2, 0 ),
									new FinalInterval( w - 1, h - 1 ) );

					final Cursor< ? > cursor = screenLabels.cursor();
					final RandomAccess< ? > ra1 = screenLabels.randomAccess();
					final RandomAccess< ? > ra2 = screenLabels.randomAccess();

					final TLongHashSet fragments = new TLongHashSet();

					final double[] min = new double[] { 0, 0, 0 };
					final double[] max = new double[] { w - 1, h - 1, 0 };
					viewerTransform.applyInverse( min, min );
					viewerTransform.applyInverse( max, max );

					while ( cursor.hasNext() )
					{
						cursor.fwd();
						ra1.setPosition( cursor );
						ra2.setPosition( cursor );
						ra1.fwd( 0 );
						ra2.fwd( 1 );

						final long[] ids1 = toIdConverter.apply( cursor.get() );
						final long[] ids2 = toIdConverter.apply( ra1.get() );
						final long[] ids3 = toIdConverter.apply( ra2.get() );
//						if ( ( ids1[ 0 ] != ids2[ 0 ] || ids1[ 0 ] != ids3[ 0 ] ) && segments.contains( assignment.getSegment( ids1[ 0 ] ) ) )
//						{
//							System.out.println( Arrays.toString( ids1 ) + " " + Arrays.toString( ids2 ) + " " + Arrays.toString( ids3 ) );
//							System.out.println( assignment.getSegment( ids1[ 0 ] ) + " " + assignment.getSegment( ids2[ 0 ] ) + " " + assignment.getSegment( ids3[ 0 ] ) );
//							System.out.println( segments );
//							System.out.println();
//						}

						for ( final long id1 : ids1 )
						{
							final long s1 = assignment.getSegment( id1 );
							if ( !segments.contains( s1 ) )
								continue;

							for ( final long id2 : ids2 )
							{
								final long s2 = assignment.getSegment( id2 );
								if ( !segments.contains( s2 ) || s1 == s2 )
									continue;
								fragments.add( id1 );
								fragments.add( id2 );
							}

							for ( final long id2 : ids3 )
							{
								final long s2 = assignment.getSegment( id2 );
								if ( !segments.contains( s2 ) || s1 == s2 )
									continue;
								fragments.add( id1 );
								fragments.add( id2 );
							}

						}

					}

					assignment.mergeFragments( fragments.toArray() );
				}
		}

	}

//	public static TLongHashSet getMergableFragmentIdsFromVisibilityOnScreen(
//			final ViewerPanel viewer,
//			final FragmentSegmentAssignment assignment, final long[] segments )
//	{
//		final TLongHashSet fragments = new TLongHashSet();
//
//		synchronized ( viewer )
//		{
//			final int w = viewer.getWidth();
//			final int h = viewer.getHeight();
//			final AffineTransform3D viewerTransform = new AffineTransform3D();
//			viewer.getState().getViewerTransform( viewerTransform );
//			final IntervalView< LabelMultisetType > screenLabels =
//					Views.interval(
//							Views.hyperSlice(
//									RealViews.affine( labels, viewerTransform ), 2, 0 ),
//							new FinalInterval( w, h ) );
//
//			for ( final LabelMultisetType pixel : Views.iterable( screenLabels ) )
//				for ( final Entry< Label > entry : pixel.entrySet() )
//					visibleIds.add( entry.getElement().id() );
//		}
//
//		return fragments;
//	}

	private class MergeFragments extends AbstractNamedBehaviour implements ClickBehaviour
	{

		private final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments;

		public MergeFragments( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
		{
			super( "merge segments" );
			this.assignments = assignments;
		}

		@Override
		public void click( final int x, final int y )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( toIdConverters.containsKey( source ) && selectedIds.containsKey( source ) && dataSources.containsKey( source ) && assignments.containsKey( source ) )
				synchronized ( viewer )
				{
					final FragmentSegmentAssignment assignment = assignments.get( source );

					final long[] selIds = selectedIds.get( source ).getActiveIds();

					if ( selIds.length < 1 )
						return;

					final Source< ? > dataSource = dataSources.get( source );

					final AffineTransform3D viewerTransform = new AffineTransform3D();
					final ViewerState state = viewer.getState();
					state.getViewerTransform( viewerTransform );
					final int level = state.getBestMipMapLevel( viewerTransform, state.getSources().stream().map( src -> src.getSpimSource() ).collect( Collectors.toList() ).indexOf( source ) );
					final AffineTransform3D affine = new AffineTransform3D();
					dataSource.getSourceTransform( 0, level, affine );
					final RealRandomAccess< ? > access = RealViews.transformReal( dataSource.getInterpolatedSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine ).realRandomAccess();
					viewer.getMouseCoordinates( access );
					access.setPosition( 0l, 2 );
					viewer.displayToGlobalCoordinates( access );
					final Object val = access.get();
					final long[] ids = toIdConverters.get( source ).apply( val );

					final TLongHashSet fragments = new TLongHashSet();
					fragments.addAll( ids );
					fragments.addAll( selIds );
					assignment.mergeFragments( fragments.toArray() );
				}
		}

	}

	private class DetachFragment extends AbstractNamedBehaviour implements ClickBehaviour
	{

		private final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments;

		public DetachFragment( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
		{
			super( "detach fragment" );
			this.assignments = assignments;
		}

		@Override
		public void click( final int x, final int y )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( toIdConverters.containsKey( source ) && dataSources.containsKey( source ) && assignments.containsKey( source ) )
				synchronized ( viewer )
				{

					final long[] selIds = selectedIds.containsKey( source ) ? selectedIds.get( source ).getActiveIds() : new long[] {};

					final FragmentSegmentAssignment assignment = assignments.get( source );

					final Source< ? > dataSource = dataSources.get( source );

					final AffineTransform3D viewerTransform = new AffineTransform3D();
					final ViewerState state = viewer.getState();
					state.getViewerTransform( viewerTransform );
					final int level = state.getBestMipMapLevel( viewerTransform, state.getSources().stream().map( src -> src.getSpimSource() ).collect( Collectors.toList() ).indexOf( source ) );
					final AffineTransform3D affine = new AffineTransform3D();
					dataSource.getSourceTransform( 0, level, affine );
					final RealTransformRealRandomAccessible< ?, InverseRealTransform > transformedSource = RealViews.transformReal( dataSource.getInterpolatedSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine );
					final RealRandomAccess< ? > access = transformedSource.realRandomAccess();
					viewer.getMouseCoordinates( access );
					access.setPosition( 0l, 2 );
					viewer.displayToGlobalCoordinates( access );
					final Object val = access.get();
					final Function< Object, long[] > toIdConverter = toIdConverters.get( source );
					final long[] ids = toIdConverter.apply( val );

					final TLongObjectHashMap< TLongHashSet > detaches = new TLongObjectHashMap<>();
					Arrays.stream( ids ).forEach( id -> {
						final TLongHashSet from = new TLongHashSet();
						Arrays.stream( selIds ).filter( sId -> sId != id ).forEach( from::add );
						detaches.put( id, from );
					} );

					detaches.forEachEntry( ( k, v ) -> {
						assignment.detachFragment( k, v.toArray() );
						return true;
					} );

				}
		}

	}

	private Optional< Source< ? > > getSource()
	{
		final int currentSource = viewer.getState().getCurrentSource();
		final List< SourceState< ? > > sources = viewer.getState().getSources();
		if ( sources.size() <= currentSource || currentSource < 0 )
			return Optional.empty();
		final Source< ? > activeSource = sources.get( currentSource ).getSpimSource();
		return Optional.of( activeSource );
	}

	private static void detachFragments(
			final RandomAccessibleInterval< ? > img,
			final Function< Object, long[] > toIdConverter,
			final FragmentSegmentAssignment assignment,
			final TLongHashSet fromSegments,
			final TLongHashSet detachFragments,
			final LongBinaryOperator fragmentIdHandler )
	{
		final Cursor< ? > cursor = Views.flatIterable( img ).cursor();

		final TLongHashSet fragments = new TLongHashSet();

		while ( cursor.hasNext() )
		{
			cursor.fwd();

			final long[] ids = toIdConverter.apply( cursor.get() );

			for ( final long id : ids )
			{
				final long segment = assignment.getSegment( id );
				if ( fromSegments.contains( segment ) )
					detachFragments.forEach( frag -> {
						fragmentIdHandler.applyAsLong( frag, id );
						return true;
					} );

			}

		}

		assignment.mergeFragments( fragments.toArray() );

	}

}
