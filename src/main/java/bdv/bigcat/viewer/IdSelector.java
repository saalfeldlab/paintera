package bdv.bigcat.viewer;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.MouseClickFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
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

	public InstallAndRemove< Node > selectFragmentWithMaximumCount( final String name, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final SelectFragmentWithMaximumCount selectFragment = new SelectFragmentWithMaximumCount();
		return new MouseClickFX( name, selectFragment::click, eventFilter );
	}

	public InstallAndRemove< Node > appendFragmentWithMaximumCount( final String name, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final AppendFragmentWithMaximumCount appendFragment = new AppendFragmentWithMaximumCount();
		return new MouseClickFX( name, appendFragment::click, eventFilter );
	}

	public InstallAndRemove< Node > merge( final String name, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		final MergeFragments merge = new MergeFragments();
		return new MouseClickFX( name, merge::click, eventFilter );
	}

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

	private abstract class SelectMaximumCount
	{

		public void click( final MouseEvent e )
		{
			final Optional< Source< ? > > optionalSource = getCurrentSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = Optional.ofNullable( sourceInfo.getState( source ).selectedIdsProperty().get() );
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

	private class MergeFragments
	{

		public void click( final MouseEvent e )
		{
			final Optional< Source< ? > > optionalSource = getCurrentSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = Optional.ofNullable( sourceInfo.getState( source ).selectedIdsProperty().get() );
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
						LOG.warn( "Merging fragments: {} -- selected ids: {}", fragments, Arrays.toString( selIds ) );
						assignments.mergeFragments( fragments.toArray() );
					}
			}
		}

	}

	private class DetachFragment
	{

		public void click( final MouseEvent e )
		{
			final Optional< Source< ? > > optionalSource = getCurrentSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = Optional.ofNullable( sourceInfo.getState( source ).selectedIdsProperty().get() );
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

					}
			}
		}

	}

	private class ConfirmSelection
	{
		public void click( final MouseEvent e )
		{
			LOG.debug( "Clicked confirm selection!" );
			final Optional< Source< ? > > optionalSource = getCurrentSource();
			if ( !optionalSource.isPresent() )
			{
				LOG.debug( "No source present!" );
				return;
			}
			final Source< ? > source = optionalSource.get();
			if ( source instanceof DataSource< ?, ? > )
			{
				final DataSource< ?, ? > dataSource = ( DataSource< ?, ? > ) source;
				final Optional< SelectedIds > selectedIds = Optional.ofNullable( sourceInfo.getState( source ).selectedIdsProperty().get() );
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

	private Optional< Source< ? > > getCurrentSource()
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
