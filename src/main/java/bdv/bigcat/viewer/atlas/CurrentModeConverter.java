package bdv.bigcat.viewer.atlas;

import java.util.Optional;

import bdv.bigcat.viewer.stream.ColorFromSegmentId;
import bdv.bigcat.viewer.stream.SeedProperty;
import bdv.bigcat.viewer.stream.WithAlpha;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class CurrentModeConverter< T, C extends Converter< T, ARGBType > & SeedProperty & WithAlpha & ColorFromSegmentId > implements Converter< T, ARGBType >, SeedProperty
{

	private C streamConverter;

	private final LongProperty seed = new SimpleLongProperty( 1 );

	private final DoubleProperty alpha = new SimpleDoubleProperty( 0x20 * 1.0 / 0xff );

	private final DoubleProperty activeFragmentAlpha = new SimpleDoubleProperty( 0xd0 * 1.0 / 0xff );

	private final DoubleProperty activeSegmentAlpha = new SimpleDoubleProperty( 0x80 * 1.0 / 0xff );

	private final BooleanProperty colorFromSegmentId = new SimpleBooleanProperty( true );

	public CurrentModeConverter()
	{
		super();
		this.streamConverter = null;
		this.alpha.set( 0x20 * 1.0 / 0xff );
		seed.addListener( ( obs, oldv, newv ) -> Optional.ofNullable( streamConverter ).ifPresent( c -> c.seedProperty().set( newv.longValue() ) ) );
	}

	@Override
	public void convert( final T s, final ARGBType t )
	{
		// TODO Auto-generated method stub
		streamConverter.convert( s, t );
	}

	public void setConverter( final C converter )
	{
		Optional.ofNullable( this.streamConverter ).ifPresent( this::unbindConverter );
		this.streamConverter = converter;
		Optional.ofNullable( this.streamConverter ).ifPresent( this::bindConverter );
	}

	@Override
	public LongProperty seedProperty()
	{
		return this.seed;
	}

	public DoubleProperty alphaProperty()
	{
		return this.alpha;
	}

	public DoubleProperty activeFragmentAlphaProperty()
	{
		return this.activeFragmentAlpha;
	}

	public DoubleProperty activeSegmentAlphaProperty()
	{
		return this.activeSegmentAlpha;
	}

	public BooleanProperty colorFromSegmentIdProperty()
	{
		return this.colorFromSegmentId;
	}

	private static int toIntegerBased( final double opacity )
	{
		return ( int ) Math.round( 255 * opacity );
	}

	private void unbindConverter( final C conv )
	{
		conv.alphaProperty().unbind();
		conv.activeFragmentAlphaProperty().unbind();
		conv.activeSegmentAlphaProperty().unbind();
		conv.colorFromSegmentIdProperty().unbind();
	}

	private void bindConverter( final C conv )
	{
		conv.alphaProperty().bind( Bindings.createIntegerBinding( () -> toIntegerBased( alpha.get() ), alpha ) );
		conv.activeFragmentAlphaProperty().bind( Bindings.createIntegerBinding( () -> toIntegerBased( activeFragmentAlpha.get() ), activeFragmentAlpha ) );
		conv.activeSegmentAlphaProperty().bind( Bindings.createIntegerBinding( () -> toIntegerBased( activeSegmentAlpha.get() ), activeSegmentAlpha ) );
		conv.colorFromSegmentIdProperty().bind( colorFromSegmentId );
	}

}
