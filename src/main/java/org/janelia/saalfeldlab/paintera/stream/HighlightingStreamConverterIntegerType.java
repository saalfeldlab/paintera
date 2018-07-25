package org.janelia.saalfeldlab.paintera.stream;

import java.util.function.ToLongFunction;

import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;

public class HighlightingStreamConverterIntegerType<I extends RealType<I>> extends HighlightingStreamConverter<I>
{

	private final ToLongFunction<I> toLong;

	public HighlightingStreamConverterIntegerType(final AbstractHighlightingARGBStream stream, final ToLongFunction<I>
			toLong)
	{
		super(stream);
		this.toLong = toLong;
	}

	@Override
	public void convert(final I input, final ARGBType output)
	{
		output.set(stream.argb(toLong.applyAsLong(input)));
	}

	public static <I extends IntegerType<I>> HighlightingStreamConverterIntegerType<I> forInteger(
			final AbstractHighlightingARGBStream stream)
	{
		return new HighlightingStreamConverterIntegerType<>(stream, I::getIntegerLong);
	}

	public static <I extends RealType<I>> HighlightingStreamConverterIntegerType<I> forRealType(
			final AbstractHighlightingARGBStream stream)
	{
		return new HighlightingStreamConverterIntegerType<>(stream, t -> (long) t.getRealDouble());
	}

}
