package bdv.bigcat.viewer.atlas.ui.source.converter;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.ARGBColorConverter;
import bdv.bigcat.viewer.atlas.CurrentModeConverter;
import bdv.bigcat.viewer.atlas.ui.BindUnbindAndNodeSupplier;
import javafx.scene.Node;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class ConverterPane implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Converter< ?, ARGBType > converter;

	private final BindUnbindAndNodeSupplier converterNode;

	public ConverterPane( final Converter< ?, ARGBType > converter )
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
		return getNodeForConverter( this.converter );
	}

	private static BindUnbindAndNodeSupplier getNodeForConverter( final Converter< ?, ARGBType > converter )
	{
		LOG.debug( "Creating node for converter: {}", converter );
		if ( converter == null )
			return BindUnbindAndNodeSupplier.empty();
		if ( converter instanceof ARGBColorConverter< ? > )
			return new ARGBColorConverterNode( ( ARGBColorConverter< ? > ) converter );
		else if ( converter instanceof CurrentModeConverter< ?, ? > )
			return new CurrentModeConverterNode( ( CurrentModeConverter< ?, ? > ) converter );
		return BindUnbindAndNodeSupplier.empty();
	}

}
