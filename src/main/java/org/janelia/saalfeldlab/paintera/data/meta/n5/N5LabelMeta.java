package org.janelia.saalfeldlab.paintera.data.meta.n5;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.meta.LabelMeta;
import org.janelia.saalfeldlab.paintera.data.meta.exception.CommitCanvasCreationFailed;
import org.janelia.saalfeldlab.paintera.data.meta.exception.IdServiceCreationFailed;
import org.janelia.saalfeldlab.paintera.data.meta.exception.SourceCreationFailed;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignment;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterIntegerType;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterLabelMultisetType;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.util.MakeUnchecked;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.Converter;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Pair;

public interface N5LabelMeta< D extends NativeType< D >, T extends Volatile< D > & NativeType< T > > extends N5Meta< D >, LabelMeta< D, T >
{

	public static < D extends NativeType< D >, T extends Volatile< D > & NativeType< T > > N5LabelMeta< D, T > forReader( N5Reader n5, String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		if ( n5 instanceof N5FSReader )
			return new N5FSLabelMeta<>( ( N5FSReader ) n5, dataset );

		if ( n5 instanceof N5HDF5Reader )
			return new N5HDF5LabelMeta<>( ( N5HDF5Reader ) n5, dataset );

		return null;
	}

	public default FragmentSegmentAssignmentState assignment(
			Optional< Pair< long[], long[] > > initialAssignment )
	{
		return initialAssignment
				.map( MakeUnchecked.function( p -> N5Helpers.assignments( writer(), dataset(), p.getA(), p.getB() ) ) )
				.orElseGet( MakeUnchecked.supplier( () -> N5Helpers.assignments( writer(), dataset() ) ) );
	}

	public default IdService idService() throws IdServiceCreationFailed
	{
		try
		{
			return N5Helpers.idService( writer(), dataset() );
		}
		catch ( final IOException e )
		{
			throw new IdServiceCreationFailed( "Unable to create n5 id service: " + e.getMessage(), e );
		}
	}

	public default BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > commitCanvas() throws CommitCanvasCreationFailed
	{
		try
		{
			return new CommitCanvasN5( writer(), dataset() );
		}
		catch ( final IOException e )
		{
			throw new CommitCanvasCreationFailed( "Unable to create n5 canvas commiter: " + e.getMessage(), e );
		}
	}

	@SuppressWarnings( "unchecked" )
	@Override
	public default LabelSourceState< D, T > asSource(
			final Optional< Pair< long[], long[] > > initialAssignment,
			final SelectedIds selectedIds,
			final Composite< ARGBType, ARGBType > composite,
			final SharedQueue sharedQueue,
			final int priority,
			final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > viewerInterpolation,
			final AffineTransform3D transform,
			final String name,
			final String canvasDir,
			final Supplier< String > canvasCacheDirUpdate,
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers,
			final Group meshesGroup,
			SourceState< ?, ? >... dependson ) throws SourceCreationFailed
	{
		try
		{
		FragmentSegmentAssignmentState assignment = assignment( initialAssignment );
		
		ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignment );
		
		boolean isMultiscale = isMultiscale();
		boolean isLabelMultisetType = isLabelMultisetType( isMultiscale );
		
		final DataSource< D, T > dataSource;
		if ( isLabelMultisetType )
		{
				dataSource = ( DataSource< D, T > ) N5Helpers.openLabelMultisetAsSource(
						reader(),
						dataset(),
						transform,
						sharedQueue,
						priority,
						name );
		}
		else
		{
			dataSource = N5Helpers.openScalarAsSource( 
					reader(), 
					dataset(), 
					transform, 
					sharedQueue, 
					priority, 
					defaultInterpolations(), 
					defaultInterpolations(), 
					name );
		}
		
		final Converter< T, ARGBType > converter = isLabelMultisetType
				? ( Converter< T, ARGBType > ) new HighlightingStreamConverterLabelMultisetType( stream )
				: ( Converter< T, ARGBType > ) highlightingStreamConverterIntegerType( stream );
			
		IdService idService = idService();
		
		@SuppressWarnings( "rawtypes" )
		LongFunction< Converter< D, BoolType > > maskForLabel = isLabelMultisetType 
				? (LongFunction< Converter< D, BoolType > >) (LongFunction) PainteraBaseView.equalMaskForLabelMultisetType()
				: (LongFunction< Converter< D, BoolType > >) (LongFunction) PainteraBaseView.equalMaskForIntegerType();
		
		DataSource< D, T > maskedSource = Masks.mask( 
				dataSource, 
				canvasDir, 
				canvasCacheDirUpdate,
				new CommitCanvasN5( writer(), dataset() ), 
				propagationExecutor );
		
		SelectedSegments selectedSegments = new SelectedSegments( selectedIds, assignment );
		FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments( selectedSegments, assignment );
		
		InterruptibleFunction< Long, Interval[] >[] blockListCache = PainteraBaseView.generateLabelBlocksForLabelCache( maskedSource, PainteraBaseView.scaleFactorsFromAffineTransforms( maskedSource ) );
		
		InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache = CacheUtils.meshCacheLoaders( maskedSource, maskForLabel, CacheUtils::toCacheSoftRefLoaderCache );
		
		
		MeshManager meshManager = new MeshManagerWithAssignment( 
				maskedSource, 
				blockListCache, 
				meshCache, 
				meshesGroup, 
				assignment, 
				fragmentsInSelectedSegments, 
				stream, 
				new SimpleIntegerProperty(), 
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				manager, 
				workers );
		
		MeshInfos meshInfos = new MeshInfos( selectedSegments, assignment, meshManager, maskedSource.getNumMipmapLevels() );
		
		return new LabelSourceState<>( 
				maskedSource, 
				converter, 
				composite, 
				name, 
				this, 
				maskForLabel, 
				assignment, 
				ToIdConverter.fromType( dataSource.getDataType() ), 
				selectedIds, 
				idService, 
				meshManager, 
				meshInfos );
		}
		catch ( RuntimeException e )
		{
			throw e;
		}
		catch ( Exception e )
		{
			throw new SourceCreationFailed( "Unable to create label source from " + this, e );
		}
	}

	public static < T > Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > defaultInterpolations()
	{
		return i -> new NearestNeighborInterpolatorFactory<>();
	}

	public static < T extends IntegerType< T > > Converter< T, ARGBType > highlightingStreamConverterIntegerType( AbstractHighlightingARGBStream stream )
	{
		return new HighlightingStreamConverterIntegerType<>( stream, IntegerType::getIntegerLong );
	}
}
