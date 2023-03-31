package bdv.fx.viewer.project;

import bdv.viewer.render.SimpleVolatileProjector;
import com.sun.javafx.image.PixelUtils;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

import java.util.concurrent.ExecutorService;

/**
 * An {@link SimpleVolatileProjector}, that renders a target 2D {@link RandomAccessibleInterval} by copying values from a
 * source {@link RandomAccessible}. The source can have more dimensions than the target. Target coordinate
 * <em>(x,y)</em> is copied from source coordinate
 * <em>(x,y,0,...,0)</em>.
 * <p>
 * A specified number of threads is used for rendering.
 *
 * @param <A> pixel type of the source {@link RandomAccessible}.
 * @author Tobias Pietzsch
 * @author Stephan Saalfeld
 * @author Philipp Hanslovsky
 */
@SuppressWarnings("restriction")
public class SimpleInterruptibleProjectorPreMultiply<A> extends SimpleVolatileProjector<A, ARGBType> {

	/**
	 * Time needed for rendering the last frame, in nano-seconds.
	 */
	protected long lastFrameRenderNanoTime;

	/**
	 * Create new projector with the given source and a converter from source to target pixel type.
	 *
	 * @param source     source pixels.
	 * @param converter  converts from the source pixel type to the target pixel type.
	 * @param target     the target interval that this projector maps to
	 * @param numThreads how many threads to use for rendering.
	 */
	public SimpleInterruptibleProjectorPreMultiply(
			final RandomAccessible<A> source,
			final Converter<? super A, ARGBType> converter,
			final RandomAccessibleInterval<ARGBType> target,
			final int numThreads) {

		this(source, wrapWithPremultiplyConverter(converter), target, numThreads, null);
	}

	public SimpleInterruptibleProjectorPreMultiply(
			final RandomAccessible<A> source,
			final Converter<? super A, ARGBType> converter,
			final RandomAccessibleInterval<ARGBType> target,
			final int numThreads,
			final ExecutorService executorService) {

		super(source, wrapWithPremultiplyConverter(converter), target, numThreads, executorService);
	}

	private static <A> Converter<A, ARGBType> wrapWithPremultiplyConverter(Converter<A, ARGBType> converter) {

		return (input, output) -> {
			converter.convert(input, output);
			output.set(PixelUtils.NonPretoPre(output.get()));
		};
	}
}
