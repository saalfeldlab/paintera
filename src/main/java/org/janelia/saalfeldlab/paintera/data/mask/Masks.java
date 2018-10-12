package org.janelia.saalfeldlab.paintera.data.mask;

import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Masks
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <D, T> DataSource<D, T> mask(
			final DataSource<D, T> source,
			final String initialCanvasPath,
			final Supplier<String> canvasCacheDirUpdate,
			final PersistCanvas mergeCanvasIntoBackground,
			final ExecutorService propagationExecutor)
	{
		LOG.debug("Masking source {}", source);
		final D d = source.getDataType();
		final T t = source.getType();
		LOG.debug("d={} t={}", d, t);

		if (d instanceof LabelMultisetType && t instanceof VolatileLabelMultisetType)
		{
			LOG.debug("Masking multiset source");
			return (DataSource<D, T>) fromLabelMultisetType(
					(DataSource<LabelMultisetType, VolatileLabelMultisetType>) source,
					initialCanvasPath,
					canvasCacheDirUpdate,
					mergeCanvasIntoBackground,
					propagationExecutor
			                                               );
		}
		else if (d instanceof IntegerType<?> && t instanceof AbstractVolatileRealType<?, ?>)
		{
			final RealType<?> i = ((AbstractVolatileRealType<?, ?>) t).get();
			if (d.getClass().isAssignableFrom(i.getClass()))
			{
				return fromIntegerType(
						(DataSource) source,
						initialCanvasPath,
						canvasCacheDirUpdate,
						mergeCanvasIntoBackground,
						propagationExecutor
				                      );
			}
		}
		LOG.debug("Do not know how to convert to masked canvas for d={} t={} -- just returning source.", d, t);
		return source;
	}

	public static <I extends IntegerType<I> & NativeType<I>, V extends AbstractVolatileRealType<I, V>> MaskedSource<I,
			V> fromIntegerType(
			final DataSource<I, V> source,
			final PersistCanvas mergeCanvasIntoBackground,
			final ExecutorService propagationExecutor)
	{
		return fromIntegerType(source, null, mergeCanvasIntoBackground, propagationExecutor);
	}

	public static <I extends IntegerType<I> & NativeType<I>, V extends AbstractVolatileRealType<I, V>> MaskedSource<I,
			V> fromIntegerType(
			final DataSource<I, V> source,
			final String initialCanvasPath,
			final PersistCanvas mergeCanvasIntoBackground,
			final ExecutorService propagationExecutor)
	{
		return fromIntegerType(
				source,
				initialCanvasPath,
				new TmpDirectoryCreator(null, null),
				mergeCanvasIntoBackground,
				propagationExecutor
		                      );
	}

	public static <I extends IntegerType<I> & NativeType<I>, V extends AbstractVolatileRealType<I, V>> MaskedSource<I,
			V> fromIntegerType(
			final DataSource<I, V> source,
			final String initialCanvasPath,
			final Supplier<String> canvasCacheDirUpdate,
			final PersistCanvas mergeCanvasIntoBackground,
			final ExecutorService propagationExecutor)
	{

		final int[][] blockSizes = new int[source.getNumMipmapLevels()][];
		for (int level = 0; level < blockSizes.length; ++level)
		{
			if (source.getDataSource(0, level) instanceof AbstractCellImg<?, ?, ?, ?>)
			{
				final int[] blockSize = new int[3];
				((AbstractCellImg<?, ?, ?, ?>) source.getDataSource(0, level)).getCellGrid().cellDimensions(blockSize);
				blockSizes[level] = blockSize;
			}
			else
			{
				blockSizes[level] = level == 0 ? new int[] {64, 64, 64} : blockSizes[level - 1];
			}
		}

		final I defaultValue = source.getDataType().createVariable();
		defaultValue.setInteger(Label.INVALID);

		final I type = source.getDataType();
		type.setInteger(Label.OUTSIDE);
		final V vtype = source.getType();
		vtype.setValid(true);
		vtype.get().setInteger(Label.OUTSIDE);

		final PickOneAllIntegerTypes<I, UnsignedLongType> pacD = new PickOneAllIntegerTypes<>(
				l -> Label.regular(l.getIntegerLong()),
				(l1, l2) -> l2.getIntegerLong() != Label.TRANSPARENT && Label.regular(l1.getIntegerLong()),
				type.createVariable()
		);

		final PickOneAllIntegerTypesVolatile<I, UnsignedLongType, V, VolatileUnsignedLongType> pacT = new
				PickOneAllIntegerTypesVolatile<>(
				l -> Label.regular(l.getIntegerLong()),
				(l1, l2) -> l2.getIntegerLong() != Label.TRANSPARENT && Label.regular(l1.getIntegerLong()),
				vtype.createVariable()
		);

		final MaskedSource<I, V> ms = new MaskedSource<>(
				source,
				blockSizes,
				canvasCacheDirUpdate,
				Optional.ofNullable(initialCanvasPath).orElseGet(canvasCacheDirUpdate),
				pacD,
				pacT,
				type,
				vtype,
				mergeCanvasIntoBackground,
				propagationExecutor
		);
		return ms;

	}

	public static MaskedSource<LabelMultisetType, VolatileLabelMultisetType> fromLabelMultisetType(
			final DataSource<LabelMultisetType, VolatileLabelMultisetType> source,
			final PersistCanvas mergeCanvasIntoBackground,
			final ExecutorService propagationExecutor)
	{
		return fromLabelMultisetType(source, null, mergeCanvasIntoBackground, propagationExecutor);
	}

	public static MaskedSource<LabelMultisetType, VolatileLabelMultisetType> fromLabelMultisetType(
			final DataSource<LabelMultisetType, VolatileLabelMultisetType> source,
			final String initialCanvasPath,
			final PersistCanvas mergeCanvasIntoBackground,
			final ExecutorService propagationExecutor)
	{
		return fromLabelMultisetType(
				source,
				initialCanvasPath,
				new TmpDirectoryCreator(null, null),
				mergeCanvasIntoBackground,
				propagationExecutor
		                            );
	}

	public static MaskedSource<LabelMultisetType, VolatileLabelMultisetType> fromLabelMultisetType(
			final DataSource<LabelMultisetType, VolatileLabelMultisetType> source,
			final String initialCanvasPath,
			final Supplier<String> canvasCacheDirUpdate,
			final PersistCanvas mergeCanvasIntoBackground,
			final ExecutorService propagationExecutor)
	{

		final int[][] blockSizes = new int[source.getNumMipmapLevels()][];
		for (int level = 0; level < blockSizes.length; ++level)
		{
			if (source.getDataSource(0, level) instanceof AbstractCellImg<?, ?, ?, ?>)
			{
				final int[] blockSize = new int[3];
				((AbstractCellImg<?, ?, ?, ?>) source.getDataSource(0, level)).getCellGrid().cellDimensions(blockSize);
				blockSizes[level] = blockSize;
			}
			else
			{
				blockSizes[level] = level == 0 ? new int[] {64, 64, 64} : blockSizes[level - 1];
			}
		}

		final LabelMultisetType defaultValue = FromIntegerTypeConverter.geAppropriateType();
		new FromIntegerTypeConverter<UnsignedLongType>().convert(new UnsignedLongType(Label.INVALID), defaultValue);

		final LabelMultisetType type = FromIntegerTypeConverter.geAppropriateType();
		new FromIntegerTypeConverter<UnsignedLongType>().convert(new UnsignedLongType(Label.OUTSIDE), defaultValue);
		final VolatileLabelMultisetType vtype = FromIntegerTypeConverter.geAppropriateVolatileType();
		new FromIntegerTypeConverter<UnsignedLongType>().convert(new UnsignedLongType(Label.OUTSIDE), defaultValue);
		vtype.setValid(true);

		final PickOneLabelMultisetType<UnsignedLongType> pacD = new PickOneLabelMultisetType<>(
				l -> Label.regular(l.getIntegerLong()),
				(l1, l2) -> l2.getIntegerLong() != Label.TRANSPARENT && Label.regular(l1.getIntegerLong())
		);

		final PickOneVolatileLabelMultisetType<UnsignedLongType, VolatileUnsignedLongType> pacT = new
				PickOneVolatileLabelMultisetType<>(
				l -> Label.regular(l.getIntegerLong()),
				(l1, l2) -> l2.getIntegerLong() != Label.TRANSPARENT && Label.regular(l1.getIntegerLong())
		);

		final MaskedSource<LabelMultisetType, VolatileLabelMultisetType> ms = new MaskedSource<>(
				source,
				blockSizes,
				canvasCacheDirUpdate,
				Optional.ofNullable(initialCanvasPath).orElseGet(canvasCacheDirUpdate),
				pacD,
				pacT,
				type,
				vtype,
				mergeCanvasIntoBackground,
				propagationExecutor
		);

		return ms;
	}

	public static Supplier<String> canvasTmpDirDirectorySupplier(final String root)
	{
		return new TmpDirectoryCreator(Paths.get(root, "canvases"), "canvas-");
	}

}
