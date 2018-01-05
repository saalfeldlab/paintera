package bdv.bigcat.viewer;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.mode.HandleMultipleIds;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.MouseClickFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.FragmentSegmentAssignment;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.labels.labelset.Label;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
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

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final Mode mode;

	public IdSelector(
			final ViewerPanelFX viewer,
			final SourceInfo sourceInfo,
			final Mode mode )
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.mode = mode;
	}

	public InstallAndRemove< Node > selectSingle( final String name, final HandleMultipleIds handleMultipleEntries, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final SelectSingle selectSingle = new SelectSingle( handleMultipleEntries );
		return new MouseClickFX( name, selectSingle::click, eventFilter );
	}

	public InstallAndRemove< Node > append( final String name, final HandleMultipleIds handleMultipleEntries, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final Append append = new Append( handleMultipleEntries );
		return new MouseClickFX( name, append::click, eventFilter );
	}

	public InstallAndRemove< Node > selectFragmentWithMaximumCount( final String name, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final SelectFragmentWithMaximumCount selectFragment = new SelectFragmentWithMaximumCount();
		return new MouseClickFX( name, selectFragment::click, eventFilter );
	}

	public InstallAndRemove< Node > selectFragmentWithMaximumCount( final String name, final Consumer< MouseEvent > otherAction, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final SelectFragmentWithMaximumCount selectFragment = new SelectFragmentWithMaximumCount();
		final Consumer< MouseEvent > handler = event -> {
			selectFragment.click( event );
			otherAction.accept( event );
		};
		return new MouseClickFX( name, handler, eventFilter );
	}

	public InstallAndRemove< Node > appendFragmentWithMaximumCount( final String name, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final AppendFragmentWithMaximumCount appendFragment = new AppendFragmentWithMaximumCount();
		return new MouseClickFX( name, appendFragment::click, eventFilter );
	}

	public InstallAndRemove< Node > appendFragmentWithMaximumCount( final String name, final Consumer< MouseEvent > otherAction, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final AppendFragmentWithMaximumCount appendFragment = new AppendFragmentWithMaximumCount();
		final Consumer< MouseEvent > handler = event -> {
			appendFragment.click( event );
			otherAction.accept( event );
		};
		return new MouseClickFX( name, handler, eventFilter );
	}

	public InstallAndRemove< Node > merge( final String name, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final MergeFragments merge = new MergeFragments();
		return new MouseClickFX( name, merge::click, eventFilter );
	}

//	public MergeSegments merge( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
//	{
//		return new MergeSegments( assignments );
//	}

	public InstallAndRemove< Node > detach( final String name, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final DetachFragment detach = new DetachFragment();
		return new MouseClickFX( name, detach::click, eventFilter );
	}

	public InstallAndRemove< Node > confirm( final String name, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final ConfirmSelection confirmSelection = new ConfirmSelection();
		return new MouseClickFX( name, confirmSelection::click, eventFilter );
	}

	private abstract class Select
	{

		public void click( final MouseEvent e )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = sourceInfo.selectedIds( source, mode );
				final Optional< ToIdConverter > toIdConverter = sourceInfo.toIdConverter( source );
				if ( selectedIds.isPresent() && toIdConverter.isPresent() )
					synchronized ( viewer )
					{
						final AffineTransform3D affine = new AffineTransform3D();
						final ViewerState state = viewer.getState();
						state.getViewerTransform( affine );
						final int level = state.getBestMipMapLevel( affine, getIndexOf( source, state ) );
						dataSource.getSourceTransform( 0, level, affine );
						final RealTransformRealRandomAccessible< ?, InverseRealTransform >.RealTransformRealRandomAccess access = RealViews.transformReal( dataSource.getInterpolatedDataSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine ).realRandomAccess();
						viewer.getMouseCoordinates( access );
						access.setPosition( 0l, 2 );
						viewer.displayToGlobalCoordinates( access );
						final Object val = access.get();
						final long[] id = toIdConverter.get().allIds( val );
						actOn( id, selectedIds.get() );
					}
			}
		}

		protected abstract void actOn( final long[] id, SelectedIds selectedIds );
	}

	private abstract class SelectMaximumCount
	{

		public void click( final MouseEvent e )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = sourceInfo.selectedIds( source, mode );
				final Optional< ToIdConverter > toIdConverter = sourceInfo.toIdConverter( source );
				if ( selectedIds.isPresent() && toIdConverter.isPresent() )
					synchronized ( viewer )
					{
						final AffineTransform3D affine = new AffineTransform3D();
						final ViewerState state = viewer.getState();
						state.getViewerTransform( affine );
						final int level = state.getBestMipMapLevel( affine, getIndexOf( dataSource, state ) );
						dataSource.getSourceTransform( 0, level, affine );
						final RealTransformRealRandomAccessible< ?, InverseRealTransform >.RealTransformRealRandomAccess access = RealViews.transformReal( dataSource.getInterpolatedDataSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine ).realRandomAccess();
						viewer.getMouseCoordinates( access );
						access.setPosition( 0l, 2 );
						viewer.displayToGlobalCoordinates( access );
						final Object val = access.get();
						final long id = toIdConverter.get().biggestFragment( val );
						actOn( id, selectedIds.get() );
					}
			}
		}

		protected abstract void actOn( final long id, SelectedIds selectedIds );
	}

	private class SelectSingle extends Select
	{

		private final HandleMultipleIds handleMultipleEntries;

		public SelectSingle( final HandleMultipleIds handleMultipleEntries )
		{
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
						LOG.debug( "ACTIVATE? {} {}", isActive[ i ], ids[ i ] );
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

	private class SelectFragmentWithMaximumCount extends SelectMaximumCount
	{

		@Override
		protected void actOn( final long id, final SelectedIds selectedIds )
		{
			if ( Label.regular( id ) )
				if ( selectedIds.isOnlyActiveId( id ) )
					selectedIds.deactivate( id );
				else
					selectedIds.activate( id );
		}
	}

	private class AppendFragmentWithMaximumCount extends SelectMaximumCount
	{

		@Override
		protected void actOn( final long id, final SelectedIds selectedIds )
		{
			if ( Label.regular( id ) )
				if ( selectedIds.isActive( id ) )
					selectedIds.deactivate( id );
				else
					selectedIds.activateAlso( id );
		}
	}

	private class Append extends Select
	{

		private final HandleMultipleIds handleMultipleEntries;

		public Append( final HandleMultipleIds handleMultipleEntries )
		{
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

	private class MergeSegments
	{

		public void click( final int x, final int y )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = sourceInfo.selectedIds( source, mode );
				final Optional< ToIdConverter > toIdConverter = sourceInfo.toIdConverter( source );
				final Optional< ? extends FragmentSegmentAssignmentState< ? > > assignmentOptional = sourceInfo.assignment( source );
				if ( toIdConverter.isPresent() && selectedIds.isPresent() && assignmentOptional.isPresent() )
					synchronized ( viewer )
					{
						final FragmentSegmentAssignmentState< ? > assignment = assignmentOptional.get();
						final long[] selIds = selectedIds.get().getActiveIds();

						if ( selIds.length < 1 )
							return;

						final long selectedId = assignment.getSegment( selIds[ 0 ] );

						for ( int i = 1; i < selIds.length; ++i )
							if ( assignment.getSegment( selIds[ i ] ) != selectedId )
							{
								LOG.warn( "Ambiguity: Selected multiple active segments -- will not apply merge!" );
								return;
							}

						final AffineTransform3D viewerTransform = new AffineTransform3D();
						final AffineTransform3D affine = new AffineTransform3D();
						final ViewerState state = viewer.getState();
						state.getViewerTransform( viewerTransform );
						final int level = state.getBestMipMapLevel( viewerTransform, getIndexOf( source, state ) );
						dataSource.getSourceTransform( 0, level, affine );
						final RealRandomAccessible< ? > interpolatedSource = dataSource.getInterpolatedDataSource( 0, level, Interpolation.NEARESTNEIGHBOR );
						final RealTransformRealRandomAccessible< ?, InverseRealTransform > transformedSource = RealViews.transformReal( interpolatedSource, affine );
						final RealRandomAccess< ? > access = transformedSource.realRandomAccess();
						viewer.getMouseCoordinates( access );
						access.setPosition( 0l, 2 );
						viewer.displayToGlobalCoordinates( access );
						final Object val = access.get();
						final long[] ids = toIdConverter.get().allIds( val );

						final TLongHashSet segments = new TLongHashSet();
						Arrays.stream( ids ).map( id -> assignment.getSegment( id ) ).forEach( segments::add );
						Arrays.stream( selIds ).map( id -> assignment.getSegment( id ) ).forEach( segments::add );

						final int w = ( int ) viewer.getWidth();
						final int h = ( int ) viewer.getHeight();
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

							final long[] ids1 = toIdConverter.get().allIds( cursor.get() );
							final long[] ids2 = toIdConverter.get().allIds( ra1.get() );
							final long[] ids3 = toIdConverter.get().allIds( ra2.get() );

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

	private class MergeFragments
	{

		public void click( final MouseEvent e )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = sourceInfo.selectedIds( source, mode );
				final Optional< ToIdConverter > toIdConverter = sourceInfo.toIdConverter( source );
				final Optional< ? extends FragmentSegmentAssignmentState< ? > > assignmentOptional = sourceInfo.assignment( source );
				if ( toIdConverter.isPresent() && selectedIds.isPresent() && assignmentOptional.isPresent() )
					synchronized ( viewer )
					{
						final FragmentSegmentAssignmentState< ? > assignments = assignmentOptional.get();

						final long[] selIds = selectedIds.get().getActiveIds();

						if ( selIds.length != 1 )
							return;

						final AffineTransform3D viewerTransform = new AffineTransform3D();
						final ViewerState state = viewer.getState();
						state.getViewerTransform( viewerTransform );
						final int level = state.getBestMipMapLevel( viewerTransform, getIndexOf( source, state ) );
						final AffineTransform3D affine = new AffineTransform3D();
						dataSource.getSourceTransform( 0, level, affine );
						final RealRandomAccess< ? > access = RealViews.transformReal( dataSource.getInterpolatedDataSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine ).realRandomAccess();
						viewer.getMouseCoordinates( access );
						access.setPosition( 0l, 2 );
						viewer.displayToGlobalCoordinates( access );
						final Object val = access.get();
						final long id = toIdConverter.get().biggestFragment( val );

						final TLongHashSet fragments = new TLongHashSet();
						fragments.add( id );
						fragments.add( selIds[ 0 ] );
						assignments.mergeFragments( fragments.toArray() );
					}
			}
		}

	}

	private class DetachFragment
	{

		public void click( final MouseEvent e )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = sourceInfo.selectedIds( source, mode );
				final Optional< ToIdConverter > toIdConverter = sourceInfo.toIdConverter( source );
				final Optional< ? extends FragmentSegmentAssignmentState< ? > > assignmentOptional = sourceInfo.assignment( source );
				if ( toIdConverter.isPresent() && selectedIds.isPresent() && assignmentOptional.isPresent() )
					synchronized ( viewer )
					{

						final FragmentSegmentAssignmentState< ? > assignment = assignmentOptional.get();

						final long[] selIds = selectedIds.get().getActiveIds();

						if ( selIds.length != 1 )
							return;

						final AffineTransform3D viewerTransform = new AffineTransform3D();
						final ViewerState state = viewer.getState();
						state.getViewerTransform( viewerTransform );
						final int level = state.getBestMipMapLevel( viewerTransform, getIndexOf( source, state ) );
						final AffineTransform3D affine = new AffineTransform3D();
						dataSource.getSourceTransform( 0, level, affine );
						final RealTransformRealRandomAccessible< ?, InverseRealTransform > transformedSource = RealViews.transformReal( dataSource.getInterpolatedDataSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine );
						final RealRandomAccess< ? > access = transformedSource.realRandomAccess();
						viewer.getMouseCoordinates( access );
						access.setPosition( 0l, 2 );
						viewer.displayToGlobalCoordinates( access );
						final Object val = access.get();
						final long id = toIdConverter.get().biggestFragment( val );

						assignment.detachFragment( id, selIds[ 0 ] );

//					final TLongObjectHashMap< TLongHashSet > detaches = new TLongObjectHashMap<>();
//					Arrays.stream( ids ).forEach( id -> {
//						final TLongHashSet from = new TLongHashSet();
//						Arrays.stream( selIds ).filter( sId -> sId != id ).forEach( from::add );
//						detaches.put( id, from );
//					} );
//
//					detaches.forEachEntry( ( k, v ) -> {
//						assignment.detachFragment( k, v.toArray() );
//						return true;
//					} );

					}
			}
		}

	}

	private class ConfirmSelection
	{
		public void click( final MouseEvent e )
		{
			LOG.debug( "Clicked confirm selection!" );
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
			{
				LOG.debug( "No source present!" );
				return;
			}
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = sourceInfo.selectedIds( source, mode );
				final Optional< ToIdConverter > toIdConverter = sourceInfo.toIdConverter( source );
				final Optional< ? extends FragmentSegmentAssignmentState< ? > > assignmentOptional = sourceInfo.assignment( source );
				if ( toIdConverter.isPresent() && selectedIds.isPresent() && assignmentOptional.isPresent() )
					synchronized ( viewer )
					{
						final FragmentSegmentAssignmentState< ? > assignment = assignmentOptional.get();

						final long[] activeFragments = selectedIds.get().getActiveIds();
						final long[] activeSegments = Arrays.stream( activeFragments ).map( id -> assignment.getSegment( id ) ).toArray();

						if ( activeSegments.length > 1 )
						{
							LOG.warn( "More than one segment active, not doing anything!" );
							return;
						}

						if ( activeSegments.length == 0 )
						{
							LOG.warn( "No segments active, not doing anything!" );
							return;
						}

						final AffineTransform3D viewerTransform = new AffineTransform3D();
						final ViewerState state = viewer.getState();
						state.getViewerTransform( viewerTransform );
						final int level = state.getBestMipMapLevel( viewerTransform, getIndexOf( source, state ) );
						final AffineTransform3D affine = new AffineTransform3D();
						dataSource.getSourceTransform( 0, level, affine );
						final RealTransformRealRandomAccessible< ?, InverseRealTransform > transformedSource = RealViews.transformReal( dataSource.getInterpolatedDataSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine );
						final RealRandomAccess< ? > access = transformedSource.realRandomAccess();
						viewer.getMouseCoordinates( access );
						access.setPosition( 0l, 2 );
						viewer.displayToGlobalCoordinates( access );
						final Object val = access.get();
						final long selectedFragment = toIdConverter.get().biggestFragment( val );
						final long selectedSegment = assignment.getSegment( selectedFragment );
						final TLongHashSet selectedSegmentsSet = new TLongHashSet( new long[] { selectedSegment } );
						final TLongHashSet visibleFragmentsSet = new TLongHashSet();

						if ( activeSegments.length == 0 || activeSegments[ 0 ] == selectedSegment )
						{
							LOG.debug( "confirm merge and separate of single segment" );
							visitEveryDisplayPixel( dataSource, viewer, obj -> visibleFragmentsSet.addAll( toIdConverter.get().allIds( obj ) ) );
							final long[] visibleFragments = visibleFragmentsSet.toArray();
							final long[] fragmentsInActiveSegment = Arrays.stream( visibleFragments ).filter( frag -> selectedSegmentsSet.contains( assignment.getSegment( frag ) ) ).toArray();
							final long[] fragmentsNotInActiveSegment = Arrays.stream( visibleFragments ).filter( frag -> !selectedSegmentsSet.contains( assignment.getSegment( frag ) ) ).toArray();
							assignment.confirmGrouping( fragmentsInActiveSegment, fragmentsNotInActiveSegment );
						}

						else
						{
							LOG.debug( "confirm merge and separate of two segments" );
							final long[] relevantSegments = new long[] { activeSegments[ 0 ], selectedSegment };
							final TLongObjectHashMap< TLongHashSet > fragmentsBySegment = new TLongObjectHashMap<>();
							Arrays.stream( relevantSegments ).forEach( seg -> fragmentsBySegment.put( seg, new TLongHashSet() ) );
							visitEveryDisplayPixel( dataSource, viewer, obj -> {
								final long[] fragments = toIdConverter.get().allIds( obj );
								for ( final long frag : fragments )
								{
									final TLongHashSet frags = fragmentsBySegment.get( assignment.getSegment( frag ) );
									if ( frags != null )
										frags.add( frag );
								}
							} );
							assignment.confirmTwoSegments( fragmentsBySegment.get( relevantSegments[ 0 ] ).toArray(), fragmentsBySegment.get( relevantSegments[ 1 ] ).toArray() );
						}

					}
			}
		}
	}

	private Optional< Source< ? > > getSource()
	{
		return Optional.ofNullable( sourceInfo.currentSourceProperty().get() );
	}

	private static int getIndexOf( final Source< ? > source, final ViewerState state )
	{
		return state
				.getSources()
				.stream()
				.map( src -> src.getSpimSource() )
				.collect( Collectors.toList() )
				.indexOf( source );
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

	private static void visitEveryDisplayPixel(
			final DataSource< ?, ? > dataSource,
			final ViewerPanelFX viewer,
			final Consumer< Object > doAtPixel )
	{
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		final ViewerState state = viewer.getState();
		state.getViewerTransform( viewerTransform );
		final int level = state.getBestMipMapLevel( viewerTransform, getIndexOf( dataSource, state ) );
		dataSource.getSourceTransform( 0, level, sourceTransform );

		final RealRandomAccessible< ? > interpolatedSource = dataSource.getInterpolatedDataSource( 0, level, Interpolation.NEARESTNEIGHBOR );
		final RealTransformRealRandomAccessible< ?, InverseRealTransform > transformedSource = RealViews.transformReal( interpolatedSource, sourceTransform );

		final int w = ( int ) viewer.getWidth();
		final int h = ( int ) viewer.getHeight();
		final IntervalView< ? > screenLabels =
				Views.interval(
						Views.hyperSlice(
								RealViews.affine( transformedSource, viewerTransform ), 2, 0 ),
						new FinalInterval( w, h ) );

		visitEveryPixel( screenLabels, doAtPixel );
	}

	private static void visitEveryPixel(
			final RandomAccessibleInterval< ? > img,
			final Consumer< Object > doAtPixel )
	{
		final Cursor< ? > cursor = Views.flatIterable( img ).cursor();

		while ( cursor.hasNext() )
			doAtPixel.accept( cursor.next() );
	}

}
