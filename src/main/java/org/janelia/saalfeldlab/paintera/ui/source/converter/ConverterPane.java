package org.janelia.saalfeldlab.paintera.ui.source.converter;

import java.lang.invoke.MethodHandles;

import javafx.scene.Node;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConverterPane implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Converter<?, ARGBType> converter;

	private final BindUnbindAndNodeSupplier converterNode;

	public ConverterPane(final Converter<?, ARGBType> converter)
	{
		super();
		this.converter = converter;
		this.converterNode = getNodeForConverter();
	}

	@Override
	public Node get()
	{
		return converterNode.get();
	}

	@Override
	public void bind()
	{
		converterNode.bind();
	}

	@Override
	public void unbind()
	{
		converterNode.unbind();
	}

	private BindUnbindAndNodeSupplier getNodeForConverter()
	{
		return getNodeForConverter(this.converter);
	}

	private static BindUnbindAndNodeSupplier getNodeForConverter(final Converter<?, ARGBType> converter)
	{
		LOG.debug("Creating node for converter: {}", converter);
		if (converter == null)
			return BindUnbindAndNodeSupplier.empty();
		if (converter instanceof ARGBColorConverter<?>)
			return new ARGBColorConverterNode((net.imglib2.converter.ARGBColorConverter<?>) converter);
		else if (converter instanceof HighlightingStreamConverter<?>)
			return new HighlightingStreamConverterNode<>((org.janelia.saalfeldlab.paintera.stream
					.HighlightingStreamConverter<?>) converter);
		else if (converter instanceof ARGBCompositeColorConverter<?, ?, ?>)
			return new ARGBCompositeColorConverterNode((ARGBCompositeColorConverter<?, ?, ?>) converter);
		return BindUnbindAndNodeSupplier.empty();
	}

}
