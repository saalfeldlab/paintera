package org.janelia.saalfeldlab.paintera.data.mask;

import java.util.Optional;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.paintera.data.DataSource;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;

public class Masks
{

	public static < I extends IntegerType< I > & NativeType< I >, V extends AbstractVolatileRealType< I, V > > MaskedSource< I, V > fromIntegerType(
			final DataSource< I, V > source,
			final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasIntoBackground )
	{
		return fromIntegerType( source, null, mergeCanvasIntoBackground );
	}

	public static < I extends IntegerType< I > & NativeType< I >, V extends AbstractVolatileRealType< I, V > > MaskedSource< I, V > fromIntegerType(
			final DataSource< I, V > source,
			final String initialCanvasPath,
			final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasIntoBackground )
	{

		final int[][] blockSizes = new int[ source.getNumMipmapLevels() ][];
		for ( int level = 0; level < blockSizes.length; ++level )
		{
			if ( source.getDataSource( 0, level ) instanceof AbstractCellImg< ?, ?, ?, ? > )
			{
				final int[] blockSize = new int[ 3 ];
				( ( AbstractCellImg< ?, ?, ?, ? > ) source.getDataSource( 0, level ) ).getCellGrid().cellDimensions( blockSize );
				blockSizes[ level ] = blockSize;
			}
			else
			{
				blockSizes[ level ] = level == 0 ? new int[] { 64, 64, 64 } : blockSizes[ level - 1 ];
			}
		}

		final I defaultValue = source.getDataType().createVariable();
		defaultValue.setInteger( deprecated.label.Label.INVALID );

		final I type = source.getDataType();
		type.setInteger( deprecated.label.Label.OUTSIDE );
		final V vtype = source.getType();
		vtype.setValid( true );
		vtype.get().setInteger( deprecated.label.Label.OUTSIDE );

		final PickOneAllIntegerTypes< I, UnsignedLongType > pacD = new PickOneAllIntegerTypes<>(
				l -> deprecated.label.Label.regular( l.getIntegerLong() ),
				( l1, l2 ) -> l2.getIntegerLong() != deprecated.label.Label.TRANSPARENT && deprecated.label.Label.regular( l1.getIntegerLong() ),
				type.createVariable() );

		final PickOneAllIntegerTypesVolatile< I, UnsignedLongType, V, VolatileUnsignedLongType > pacT = new PickOneAllIntegerTypesVolatile<>(
				l -> deprecated.label.Label.regular( l.getIntegerLong() ),
				( l1, l2 ) -> l2.getIntegerLong() != deprecated.label.Label.TRANSPARENT && deprecated.label.Label.regular( l1.getIntegerLong() ),
				vtype.createVariable() );

		final MaskedSource< I, V > ms = new MaskedSource<>(
				source,
				blockSizes,
				new TmpDirectoryCreator( null, null ),
				Optional.ofNullable( initialCanvasPath ).orElse( new TmpDirectoryCreator( null, null ).get() ),
				pacD,
				pacT,
				type,
				vtype,
				mergeCanvasIntoBackground );
		return ms;

	}

	public static MaskedSource< LabelMultisetType, VolatileLabelMultisetType > fromLabelMultisetType(
			final DataSource< LabelMultisetType, VolatileLabelMultisetType > source,
			final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasIntoBackground )
	{
		return fromLabelMultisetType( source, null, mergeCanvasIntoBackground );
	}

	public static MaskedSource< LabelMultisetType, VolatileLabelMultisetType > fromLabelMultisetType(
			final DataSource< LabelMultisetType, VolatileLabelMultisetType > source,
			final String initialCanvasPath,
			final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasIntoBackground )
	{

		final int[][] blockSizes = new int[ source.getNumMipmapLevels() ][];
		for ( int level = 0; level < blockSizes.length; ++level )
		{
			if ( source.getDataSource( 0, level ) instanceof AbstractCellImg< ?, ?, ?, ? > )
			{
				final int[] blockSize = new int[ 3 ];
				( ( AbstractCellImg< ?, ?, ?, ? > ) source.getDataSource( 0, level ) ).getCellGrid().cellDimensions( blockSize );
				blockSizes[ level ] = blockSize;
			}
			else
			{
				blockSizes[ level ] = level == 0 ? new int[] { 64, 64, 64 } : blockSizes[ level - 1 ];
			}
		}

		final LabelMultisetType defaultValue = FromIntegerTypeConverter.geAppropriateType();
		new FromIntegerTypeConverter< UnsignedLongType >().convert( new UnsignedLongType( deprecated.label.Label.INVALID ), defaultValue );

		final LabelMultisetType type = FromIntegerTypeConverter.geAppropriateType();
		new FromIntegerTypeConverter< UnsignedLongType >().convert( new UnsignedLongType( deprecated.label.Label.OUTSIDE ), defaultValue );
		final VolatileLabelMultisetType vtype = FromIntegerTypeConverter.geAppropriateVolatileType();
		new FromIntegerTypeConverter< UnsignedLongType >().convert( new UnsignedLongType( deprecated.label.Label.OUTSIDE ), defaultValue );
		vtype.setValid( true );

		final PickOneLabelMultisetType< UnsignedLongType > pacD = new PickOneLabelMultisetType<>(
				l -> deprecated.label.Label.regular( l.getIntegerLong() ),
				( l1, l2 ) -> l2.getIntegerLong() != deprecated.label.Label.TRANSPARENT && deprecated.label.Label.regular( l1.getIntegerLong() ) );

		final PickOneVolatileLabelMultisetType< UnsignedLongType, VolatileUnsignedLongType > pacT = new PickOneVolatileLabelMultisetType<>(
				l -> deprecated.label.Label.regular( l.getIntegerLong() ),
				( l1, l2 ) -> l2.getIntegerLong() != deprecated.label.Label.TRANSPARENT && deprecated.label.Label.regular( l1.getIntegerLong() ) );

		final MaskedSource< LabelMultisetType, VolatileLabelMultisetType > ms = new MaskedSource<>(
				source,
				blockSizes,
				new TmpDirectoryCreator( null, null ),
				Optional.ofNullable( initialCanvasPath ).orElse( new TmpDirectoryCreator( null, null ).get() ),
				pacD,
				pacT,
				type,
				vtype,
				mergeCanvasIntoBackground );

		return ms;
	}

}
