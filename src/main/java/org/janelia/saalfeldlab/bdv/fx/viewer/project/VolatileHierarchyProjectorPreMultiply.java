package org.janelia.saalfeldlab.bdv.fx.viewer.project;

import com.sun.javafx.image.PixelUtils;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;

import java.util.List;

public class VolatileHierarchyProjectorPreMultiply<A extends Volatile<?>> extends VolatileHierarchyProjector<A, ARGBType> {

	public VolatileHierarchyProjectorPreMultiply(
			final List<? extends RandomAccessible<A>> sources,
			final Converter<? super A, ARGBType> converter,
			final RandomAccessibleInterval<ARGBType> target,
			final RandomAccessibleInterval<ByteType> mask,
			final TaskExecutor taskExecutor) {

		super(sources, getPremultiplyConverter(converter), target, mask, taskExecutor);
	}

	private static <A> Converter<A, ARGBType> getPremultiplyConverter(Converter<A, ARGBType> converter) {
		return (source, target) -> {
			converter.convert(source, target);
			target.set(PixelUtils.NonPretoPre(target.get()));
		};
	}
}
