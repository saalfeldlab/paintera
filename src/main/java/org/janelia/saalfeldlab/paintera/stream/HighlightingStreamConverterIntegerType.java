package org.janelia.saalfeldlab.paintera.stream;

import net.imglib2.Volatile;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class HighlightingStreamConverterIntegerType<I extends IntegerType<I>> extends HighlightingStreamConverter<I> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public HighlightingStreamConverterIntegerType(final AbstractHighlightingARGBStream stream) {

		super(stream);
		LOG.debug("Created {} from stream {}", this.getClass(), stream);
	}

	@Override
	public void convert(final I input, final ARGBType output) {

		output.set(stream.argb(input.getIntegerLong()));
	}

}
