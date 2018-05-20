package org.janelia.saalfeldlab.paintera.state;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.Interpolations;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerSimple;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;

import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.Multiset.Entry;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValueTriple;
import tmp.bdv.img.cache.VolatileCachedCellImg;
import tmp.net.imglib2.cache.ref.WeakRefVolatileCache;

public class IntersectingSourceState
		extends MinimalSourceState< UnsignedByteType, VolatileUnsignedByteType, DataSource< UnsignedByteType, VolatileUnsignedByteType >, Converter< VolatileUnsignedByteType, ARGBType > >
{

	private final MeshManagerSimple meshManager;

	public < B extends BooleanType< B > > IntersectingSourceState(
			final SourceState< B, Volatile< B > > thresholded,
			final LabelSourceState< ?, ? > labels,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final SharedQueue queue,
			final int priority,
			final Group meshesGroup,
			final ExecutorService manager,
			final ExecutorService workers )
	{
		// TODO use better converter
		super(
				makeIntersect( thresholded, labels, queue, priority, name ),
				new ARGBColorConverter.Imp0<>( 0, 1 ),
				composite,
				name,
				thresholded,
				labels );
		final DataSource< UnsignedByteType, VolatileUnsignedByteType > source = getDataSource();

		final MeshManager< TLongHashSet > meshManager = labels.meshManager();

		final SelectedIds selectedIds = labels.selectedIds();
		final InterruptibleFunction< ShapeKey< Long >, Pair< float[], float[] > >[] meshCaches = CacheUtils.meshCacheLoaders(
				source,
				l -> ( s, t ) -> t.set( s.get() > 0 ),
				CacheUtils::toCacheSoftRefLoaderCache );

		this.meshManager = new MeshManagerSimple(
				meshManager.blockListCache(),
				meshCaches,
				meshesGroup,
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				manager,
				workers );

		selectedIds.addListener( obs -> {
			for ( int level = 0; level < source.getNumMipmapLevels(); ++level )
			{
				final RandomAccessibleInterval< UnsignedByteType > data = source.getDataSource( 0, level );
				if ( data instanceof CachedCellImg< ?, ? > )
				{
					( ( CachedCellImg< ?, ? > ) data ).getCache().invalidateAll();
				}

				final RandomAccessibleInterval< VolatileUnsignedByteType > vdata = source.getSource( 0, level );
				if ( data instanceof VolatileCachedCellImg< ?, ? > )
				{
					( ( VolatileCachedCellImg< ?, ? > ) vdata ).getInvalidateAll().run();
				}

			}
			if ( Optional.ofNullable( selectedIds.getActiveIds() ).map( sel -> sel.length ).orElse( 0 ) > 0 )
			{
				this.meshManager.generateMesh( 1 );
			}
			else
			{
				this.meshManager.removeAllMeshes();
			}
		} );

	}

	public MeshManager< Long > meshManager()
	{
		return this.meshManager;
	}

	private static < D, T, B extends BooleanType< B > > DataSource< UnsignedByteType, VolatileUnsignedByteType > makeIntersect(
			final SourceState< B, Volatile< B > > thresholded,
			final LabelSourceState< D, T > labels,
			final SharedQueue queue,
			final int priority,
			final String name )
	{
		if ( thresholded.getDataSource().getNumMipmapLevels() != labels.getDataSource().getNumMipmapLevels() ) { throw new RuntimeException( "Incompatible sources (num mip map levels )" ); }

		final AffineTransform3D[] transforms = new AffineTransform3D[ thresholded.getDataSource().getNumMipmapLevels() ];
		final RandomAccessibleInterval< UnsignedByteType >[] data = new RandomAccessibleInterval[ transforms.length ];
		final RandomAccessibleInterval< VolatileUnsignedByteType >[] vdata = new RandomAccessibleInterval[ transforms.length ];

		final SelectedIds selectedIds = labels.selectedIds();
		final FragmentSegmentAssignmentState assignment = labels.assignment();
		final SelectedSegments selectedSegments = new SelectedSegments( selectedIds, assignment );
		final FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments( selectedSegments, assignment );

		for ( int level = 0; level < thresholded.getDataSource().getNumMipmapLevels(); ++level )
		{
			final AffineTransform3D tf1 = new AffineTransform3D();
			final AffineTransform3D tf2 = new AffineTransform3D();
			thresholded.getDataSource().getSourceTransform( 0, level, tf1 );
			labels.getDataSource().getSourceTransform( 0, level, tf2 );
			if ( !Arrays.equals( tf1.getRowPackedCopy(), tf2.getRowPackedCopy() ) ) { throw new RuntimeException( "Incompatible sources ( transforms )" ); }

			final RandomAccessibleInterval< B > thresh = thresholded.getDataSource().getDataSource( 0, level );
			final RandomAccessibleInterval< D > label = labels.getDataSource().getDataSource( 0, level );

			final CellGrid grid = label instanceof AbstractCellImg< ?, ?, ?, ? >
					? ( ( AbstractCellImg< ?, ?, ?, ? > ) label ).getCellGrid()
					: new CellGrid( Intervals.dimensionsAsLongArray( label ), Arrays.stream( Intervals.dimensionsAsLongArray( label ) ).mapToInt( l -> ( int ) l ).toArray() );

			final B extension = Util.getTypeFromInterval( thresh );
			extension.set( false );
			final LabelIntersectionCellLoader< D, B > loader = new LabelIntersectionCellLoader<>(
					label,
					thresh,
					checkForType( labels.getDataSource().getDataType(), fragmentsInSelectedSegments ),
					BooleanType::get,
					extension::copy );

			final Set< AccessFlags > accessFlags = AccessFlags.setOf( AccessFlags.VOLATILE );
			final Cache< Long, Cell< VolatileByteArray > > cache = new SoftRefLoaderCache< Long, Cell< VolatileByteArray > >()
					.withLoader( LoadedCellCacheLoader.get( grid, loader, new UnsignedByteType(), accessFlags ) );

			final CachedCellImg< UnsignedByteType, VolatileByteArray > img = new CachedCellImg<>( grid, new UnsignedByteType(), cache, ArrayDataAccessFactory.get( PrimitiveType.BYTE, accessFlags ) );
			final CreateInvalid< Long, Cell< VolatileByteArray > > createInvalid = CreateInvalidVolatileCell.get( grid, new VolatileUnsignedByteType(), false );
			final VolatileCache< Long, Cell< VolatileByteArray > > volatileCache = new WeakRefVolatileCache<>( cache, queue, createInvalid );
			final CacheHints hints = new CacheHints( LoadingStrategy.VOLATILE, priority, false );
			final VolatileCachedCellImg< VolatileUnsignedByteType, VolatileByteArray > volatileImg =
					new VolatileCachedCellImg<>(
							grid,
							new VolatileUnsignedByteType(),
							hints,
							volatileCache.unchecked()::get,
							volatileCache::invalidateAll );
			final RandomAccessibleInterval< VolatileUnsignedByteType > vimg = VolatileViews.wrapAsVolatile(
					img,
					queue,
					new CacheHints( LoadingStrategy.VOLATILE, priority, false ) );
			data[ level ] = img;
			vdata[ level ] = vimg;

		}

		return new RandomAccessibleIntervalDataSource<>(
				new ValueTriple<>( data, vdata, transforms ),
				Interpolations.nearestNeighbor(),
				Interpolations.nearestNeighbor(),
				name );
	}

	private static < T > Predicate< T > checkForType( final T t, final FragmentsInSelectedSegments fragmentsInSelectedSegments )
	{
		if ( t instanceof LabelMultisetType ) { return ( Predicate< T > ) checkForLabelMultisetType( fragmentsInSelectedSegments ); }

		return null;
	}

	private static final Predicate< LabelMultisetType > checkForLabelMultisetType( final FragmentsInSelectedSegments fragmentsInSelectedSegments )
	{
		return lmt -> {
			for ( final Entry< Label > entry : lmt.entrySet() )
			{
				if ( fragmentsInSelectedSegments.contains( entry.getElement().id() ) ) { return true; }
			}
			return false;
		};
	}

}
