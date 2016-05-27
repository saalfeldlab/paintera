package bdv.img.h5;

import static bdv.img.hdf5.Util.reorder;

import java.io.File;
import java.util.Arrays;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.img.labelpair.RandomAccessiblePair;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultiset;
import bdv.labels.labelset.LabelMultisetType;
import ch.systemsx.cisd.base.mdarray.MDLongArray;
import ch.systemsx.cisd.hdf5.HDF5DataTypeInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.HDF5IntStorageFeatures;
import ch.systemsx.cisd.hdf5.IHDF5LongReader;
import ch.systemsx.cisd.hdf5.IHDF5LongWriter;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.Dimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

/**
 * Utility methods for simple HDF5 files.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class H5Utils
{
	static public void cropCellDimensions(
			final Dimensions sourceDimensions,
			final long[] offset,
			final int[] cellDimensions,
			final long[] croppedCellDimensions )
	{
		final int n = sourceDimensions.numDimensions();
		for ( int d = 0; d < n; ++d )
			croppedCellDimensions[ d ] = Math.min( cellDimensions[ d ], sourceDimensions.dimension( d ) - offset[ d ] );
	}

	/**
	 * Load an HDF5 uint64 dataset into a {@link CellImg} of {@link LongType}.
	 *
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public CellImg< LongType, ?, ? > loadUnsignedLong(
			final File file,
			final String dataset,
			final int[] cellDimensions )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( file );
		final IHDF5LongReader uint64Reader = reader.uint64();

		final long[] dimensions = reorder( reader.object().getDimensions( dataset ) );
		final int n = dimensions.length;

		final CellImg< LongType, ?, ? > target = new CellImgFactory< LongType >( cellDimensions ).create( dimensions, new LongType() );

		final long[] offset = new long[ n ];
		final long[] targetCellDimensions = new long[ n ];
		for ( int d = 0; d < n; )
		{
			cropCellDimensions( target, offset, cellDimensions, targetCellDimensions );
			final RandomAccessibleInterval< LongType > targetBlock = Views.offsetInterval( target, offset, targetCellDimensions );
			final MDLongArray targetCell = uint64Reader.readMDArrayBlockWithOffset(
					dataset,
					Util.long2int( reorder( targetCellDimensions ) ),
					reorder( offset ) );

			int i = 0;
			for ( final LongType t : Views.flatIterable( targetBlock ) )
				t.set( targetCell.get( i++ ) );

			for ( d = 0; d < n; ++d )
			{
				offset[ d ] += cellDimensions[ d ];
				if ( offset[ d ] < dimensions[ d ] )
					break;
				else
					offset[ d ] = 0;
			}

//			System.out.println( Util.printCoordinates( offset ) );
		}
		reader.close();

		return target;
	}

	/**
	 * Save a {@link RandomAccessibleInterval} of {@link LongType} into an HDF5
	 * uint64 dataset.
	 *
	 * @param source
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public void saveUnsignedLong(
			final RandomAccessibleInterval< LongType > source,
			final File file,
			final String dataset,
			final int[] cellDimensions )
	{
		final int n = source.numDimensions();
		final long[] dimensions = Intervals.dimensionsAsLongArray( source );
		final IHDF5Writer writer = HDF5Factory.open( file );
		final IHDF5LongWriter uint64Writer = writer.uint64();
		if ( !writer.exists( dataset ) )
			uint64Writer.createMDArray(
					dataset,
					reorder( dimensions ),
					reorder( cellDimensions ),
					HDF5IntStorageFeatures.INT_AUTO_SCALING_DEFLATE );

		final long[] offset = new long[ n ];
		final long[] sourceCellDimensions = new long[ n ];
		for ( int d = 0; d < n; )
		{
			cropCellDimensions( source, offset, cellDimensions, sourceCellDimensions );
			final RandomAccessibleInterval< LongType > sourceBlock = Views.offsetInterval( source, offset, sourceCellDimensions );
			final MDLongArray targetCell = new MDLongArray( reorder( sourceCellDimensions ) );
			int i = 0;
			for ( final LongType t : Views.flatIterable( sourceBlock ) )
				targetCell.set( t.get(), i++ );

			uint64Writer.writeMDArrayBlockWithOffset( dataset, targetCell, reorder( offset ) );

			for ( d = 0; d < n; ++d )
			{
				offset[ d ] += cellDimensions[ d ];
				if ( offset[ d ] < source.dimension( d ) )
					break;
				else
					offset[ d ] = 0;
			}

//			System.out.println( Util.printCoordinates( offset ) );
		}
		writer.close();
	}

	/**
	 * Save the combination of a single element {@link LabelMultiset} source
	 * and a {@link LongType} overlay with transparent pixels into an HDF5
	 * uint64 dataset.
	 *
	 * @param labelMultisetSource the background
	 * @param labelSource the overlay
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public void saveSingleElementLabelMultisetLongPair(
			final RandomAccessible< LabelMultisetType > labelMultisetSource,
			final RandomAccessible< LongType > labelSource,
			final Interval interval,
			final File file,
			final String dataset,
			final int[] cellDimensions )
	{
		assert
				labelMultisetSource.numDimensions() == labelSource.numDimensions() &&
				labelSource.numDimensions() == interval.numDimensions() : "input dimensions do not match";

		final RandomAccessiblePair< LabelMultisetType, LongType > pair = new RandomAccessiblePair<>( labelMultisetSource, labelSource );
		final RandomAccessibleInterval< Pair< LabelMultisetType, LongType > > pairInterval = Views.offsetInterval( pair, interval );
		final Converter< Pair< LabelMultisetType, LongType >, LongType > converter =
				new Converter< Pair< LabelMultisetType, LongType >, LongType >()
				{
					@Override
					public void convert(
							final Pair< LabelMultisetType, LongType > input,
							final LongType output )
					{
						final long inputB = input.getB().get();
						if ( inputB == Label.TRANSPARENT )
						{
							output.set( input.getA().entrySet().iterator().next().getElement().id() );
						}
						else
						{
							output.set( inputB );
						}
					}
				};

		final RandomAccessibleInterval< LongType > source =
				Converters.convert(
						pairInterval,
						converter,
						new LongType() );

		saveUnsignedLong( source, file, dataset, cellDimensions );
	}

	/**
	 * Save the combination of a single element {@link LabelMultiset} source
	 * and a fragment to segment assignment table and a {@link LongType}
	 * overlay with transparent pixels into an HDF5 uint64 dataset.
	 *
	 * @param labelMultisetSource the background
	 * @param labelSource the overlay
	 * @param interval the interval to be saved
	 * @param assignment fragmetn to segment assignment
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public void saveAssignedSingleElementLabelMultisetLongPair(
			final RandomAccessible< LabelMultisetType > labelMultisetSource,
			final RandomAccessible< LongType > labelSource,
			final Interval interval,
			final FragmentSegmentAssignment assignment,
			final File file,
			final String dataset,
			final int[] cellDimensions )
	{
		assert
				labelMultisetSource.numDimensions() == labelSource.numDimensions() &&
				labelSource.numDimensions() == interval.numDimensions() : "input dimensions do not match";

		final RandomAccessiblePair< LabelMultisetType, LongType > pair = new RandomAccessiblePair<>( labelMultisetSource, labelSource );
		final RandomAccessibleInterval< Pair< LabelMultisetType, LongType > > pairInterval = Views.offsetInterval( pair, interval );
		final Converter< Pair< LabelMultisetType, LongType >, LongType > converter =
				new Converter< Pair< LabelMultisetType, LongType >, LongType >()
				{
					@Override
					public void convert(
							final Pair< LabelMultisetType, LongType > input,
							final LongType output )
					{
						final long inputB = input.getB().get();
						if ( inputB == Label.TRANSPARENT )
						{
							output.set( assignment.getSegment( input.getA().entrySet().iterator().next().getElement().id() ) );
						}
						else
						{
							output.set( assignment.getSegment( inputB ) );
						}
					}
				};

		final RandomAccessibleInterval< LongType > source =
				Converters.convert(
						pairInterval,
						converter,
						new LongType() );

		saveUnsignedLong( source, file, dataset, cellDimensions );
	}

	/**
	 * Load a long to long lookup table from an HDF5 dataset
	 *
	 * @param reader
	 * @param dataset
	 * @param blockSize
	 */
	static public TLongLongHashMap loadLongLongLut(
			final IHDF5Reader reader,
			final String dataset,
			final int blockSize )
	{
		final IHDF5LongReader uint64Reader = reader.uint64();

		if ( !reader.exists( dataset ) )
			return null;

		final long[] dimensions = reader.object().getDimensions( dataset );
		if ( !( dimensions.length == 2 && dimensions[ 0 ] == 2 ) )
		{
			System.err.println( "LUT is not a lookup table, dimensions = " + Arrays.toString( dimensions ) );
			return null;
		}

		final long size = dimensions[ 1 ];

		final TLongLongHashMap lut = new TLongLongHashMap(
				Constants.DEFAULT_CAPACITY,
				Constants.DEFAULT_LOAD_FACTOR,
				Label.TRANSPARENT,
				Label.TRANSPARENT );

		for ( int offset = 0; offset < size; offset += blockSize )
		{
			final MDLongArray block = uint64Reader.readMDArrayBlockWithOffset(
					dataset,
					new int[]{ 2, ( int )Math.min( blockSize, size - offset ) },
					new long[]{ 0, offset } );

			for ( int i = 0; i < block.size( 1 ); ++i )
				lut.put( block.get( 0, i ), block.get( 1, i ) );

		}

		return lut;
	}

	/**
	 * Load a long to long lookup table from an HDF5 dataset.
	 *
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public TLongLongHashMap loadLongLongLut(
			final File file,
			final String dataset,
			final int blockSize )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( file );
		final TLongLongHashMap lut = loadLongLongLut( reader, dataset, blockSize );
		reader.close();
		return lut;
	}

	/**
	 * Load a long to long lookup table from an HDF5 dataset.
	 *
	 * @param filePath
	 * @param dataset
	 * @param cellDimensions
	 */
	static public TLongLongHashMap loadLongLongLut(
			final String filePath,
			final String dataset,
			final int blockSize )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( filePath );
		final TLongLongHashMap lut = loadLongLongLut( reader, dataset, blockSize );
		reader.close();
		return lut;
	}

	/**
	 * Save a long to long lookup table into an HDF5 uint64 dataset.
	 *
	 * @param lut
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public void saveLongLongLut(
			final TLongLongHashMap lut,
			final IHDF5Writer writer,
			final String dataset,
			final int blockSize )
	{
		final IHDF5LongWriter uint64Writer = writer.uint64();
		if ( !writer.exists( dataset ) )
			uint64Writer.createMDArray(
					dataset,
					new long[]{ 2, lut.size() },
					new int[]{ 2, blockSize },
					HDF5IntStorageFeatures.INT_AUTO_SCALING_DEFLATE );

		final long[] keys = lut.keys();
		for ( int offset = 0, i = 0; offset < lut.size(); offset += blockSize )
		{
			final int size = ( int )Math.min( blockSize, lut.size() - offset );
			final MDLongArray targetCell = new MDLongArray( new int[]{ 2, size } );
			for ( int j = 0; j < size; ++j, ++i )
			{
				targetCell.set( keys[ i ], 0, j );
				targetCell.set( lut.get( keys[ i ] ), 1, j );
			}

			uint64Writer.writeMDArrayBlockWithOffset( dataset, targetCell, new long[]{ 0, offset } );
		}
	}

	/**
	 * Save a long to long lookup table into an HDF5 uint64 dataset.
	 *
	 * @param lut
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public void saveLongLongLut(
			final TLongLongHashMap lut,
			final File file,
			final String dataset,
			final int blockSize )
	{
		final IHDF5Writer writer = HDF5Factory.open( file );
		saveLongLongLut( lut, writer, dataset, blockSize );
		writer.close();
	}

	/**
	 * Save a long to long lookup table into an HDF5 uint64 dataset.
	 *
	 * @param lut
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public void saveLongLongLut(
			final TLongLongHashMap lut,
			final String filePath,
			final String dataset,
			final int blockSize )
	{
		final IHDF5Writer writer = HDF5Factory.open( filePath );
		saveLongLongLut( lut, writer, dataset, blockSize );
		writer.close();
	}

	/**
	 * Load an attribute from of an HDF5 object.
	 *
	 * @param lut
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public < T > T loadAttribute(
			final IHDF5Reader reader,
			final String object,
			final String attribute )
	{
		if ( !reader.exists( object ) )
			return null;

		if ( !reader.object().hasAttribute( object, attribute ) )
			return null;

		final HDF5DataTypeInformation attributeInfo = reader.object().getAttributeInformation( object, attribute );
		final Class< ? > type = attributeInfo.tryGetJavaType();
		System.out.println( "class: " + type );
		if ( type.isAssignableFrom( long.class ) )
		{
			if ( attributeInfo.isSigned() )
				return ( T )new Long( reader.int64().getAttr( object, attribute ) );
			else
				return ( T )new Long( reader.uint64().getAttr( object, attribute ) );
		}
		else if ( type.isAssignableFrom( int.class ) )
		{
			if ( attributeInfo.isSigned() )
				return ( T )new Integer( reader.int32().getAttr( object, attribute ) );
			else
				return ( T )new Integer( reader.uint32().getAttr( object, attribute ) );
		}
		else if ( type.isAssignableFrom( short.class ) )
		{
			if ( attributeInfo.isSigned() )
				return ( T )new Short( reader.int16().getAttr( object, attribute ) );
			else
				return ( T )new Short( reader.uint16().getAttr( object, attribute ) );
		}
		else if ( type.isAssignableFrom( byte.class ) )
		{
			if ( attributeInfo.isSigned() )
				return ( T )new Byte( reader.int8().getAttr( object, attribute ) );
			else
				return ( T )new Byte( reader.uint8().getAttr( object, attribute ) );
		}
		else if ( type.isAssignableFrom( double.class ) )
			return ( T )new Double( reader.float64().getAttr( object, attribute ) );
		else if ( type.isAssignableFrom( float.class ) )
			return ( T )new Double( reader.float32().getAttr( object, attribute ) );
		else if ( type.isAssignableFrom( String.class ) )
			return ( T )new String( reader.string().getAttr( object, attribute ) );

		System.out.println( "Reading attributes of type " + attributeInfo + " not yet implemented." );
		return null;
	}

	/**
	 * Load an attribute from of an HDF5 object.
	 *
	 * @param lut
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public < T > T loadAttribute(
			final File file,
			final String object,
			final String attribute )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( file );
		final T t = loadAttribute( reader, object, attribute );
		reader.close();
		return t;
	}

	/**
	 * Load an attribute from of an HDF5 object.
	 *
	 * @param lut
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public < T > T loadAttribute(
			final String filePath,
			final String object,
			final String attribute )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( filePath );
		final T t = loadAttribute( reader, object, attribute );
		reader.close();
		return t;
	}

	/**
	 * Save a long value as a uint64 attribute of an HDF5 object.
	 *
	 * @param value
	 * @param writer
	 * @param object
	 * @param attribute
	 */
	static public void saveUint64Attribute(
			final long value,
			final IHDF5Writer writer,
			final String object,
			final String attribute )
	{
		if ( !writer.exists( object ) )
			writer.object().createGroup( object );

		// TODO Bug in JHDF5, does not save the value most of the time when using the non-deprecated method
//		writer.uint64().setAttr( object, attribute, value );
		writer.setLongAttribute( object, attribute, value );
//		writer.file().flush();
	}

	/**
	 * Save a long value as a uint64 attribute of an HDF5 object.
	 *
	 * @param value
	 * @param file
	 * @param object
	 * @param attribute
	 */
	static public void saveUint64Attribute(
			final long value,
			final File file,
			final String object,
			final String attribute )
	{
		final IHDF5Writer writer = HDF5Factory.open( file );
		saveUint64Attribute( value, writer, object, attribute );
		writer.close();
	}

	/**
	 * Save a long value as a uint64 attribute of an HDF5 object.
	 *
	 * @param value
	 * @param filePath
	 * @param object
	 * @param attribute
	 */
	static public void saveUint64Attribute(
			final long value,
			final String filePath,
			final String object,
			final String attribute )
	{
		final IHDF5Writer writer = HDF5Factory.open( filePath );
		saveUint64Attribute( value, writer, object, attribute );
		writer.close();
	}
}
