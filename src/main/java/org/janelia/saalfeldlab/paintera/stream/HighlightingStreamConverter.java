package org.janelia.saalfeldlab.paintera.stream;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import net.imglib2.converter.Converter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;

public abstract class HighlightingStreamConverter< T > implements Converter< T, ARGBType >, SeedProperty, WithAlpha, ColorFromSegmentId
{

	protected final AbstractHighlightingARGBStream stream;

	private final LongProperty seed = new SimpleLongProperty( 1 );

	private final IntegerProperty alpha = new SimpleIntegerProperty();

	private final IntegerProperty activeFragmentAlpha = new SimpleIntegerProperty();

	private final IntegerProperty activeSegmentAlpha = new SimpleIntegerProperty();

	private final BooleanProperty colorFromSegmentId = new SimpleBooleanProperty( true );

	public HighlightingStreamConverter( final AbstractHighlightingARGBStream stream )
	{
		super();
		this.stream = stream;
		seed.addListener( ( obs, oldv, newv ) -> stream.setSeed( newv.longValue() ) );
		alpha.addListener( ( obs, oldv, newv ) -> stream.setAlpha( newv.intValue() ) );
		activeFragmentAlpha.addListener( ( obs, oldv, newv ) -> stream.setActiveFragmentAlpha( newv.intValue() ) );
		activeSegmentAlpha.addListener( ( obs, oldv, newv ) -> stream.setActiveSegmentAlpha( newv.intValue() ) );
		stream.setSeed( seed.get() );
		alpha.set( stream.getAlpha() );
		activeFragmentAlpha.set( stream.getActiveFragmentAlpha() );
		activeSegmentAlpha.set( stream.getActiveSegmentAlpha() );
		stream.colorFromSegmentIdProperty().bind( this.colorFromSegmentId );
	}

	private static long considerMaxUnsignedInt( final long val )
	{
		return val >= Integer.MAX_VALUE ? Label.INVALID : val;
	}

	@Override
	public LongProperty seedProperty()
	{
		return this.seed;
	}

	@Override
	public IntegerProperty alphaProperty()
	{
		return this.alpha;
	}

	@Override
	public IntegerProperty activeFragmentAlphaProperty()
	{
		return this.activeFragmentAlpha;
	}

	@Override
	public IntegerProperty activeSegmentAlphaProperty()
	{
		return this.activeSegmentAlpha;
	}

	@Override
	public BooleanProperty colorFromSegmentIdProperty()
	{
		return this.colorFromSegmentId;
	}

	public AbstractHighlightingARGBStream getStream()
	{
		return this.stream;
	}

	@SuppressWarnings( "unchecked" )
	public static < T > HighlightingStreamConverter< T > forType(
			final AbstractHighlightingARGBStream stream,
			final T t )
	{
		if ( t instanceof IntegerType< ? > ) { return ( org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter< T > ) HighlightingStreamConverterIntegerType.forInteger( stream ); }
		if ( t instanceof RealType< ? > ) { return ( org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter< T > ) HighlightingStreamConverterIntegerType.forRealType( stream ); }
		if ( t instanceof LabelMultisetType || t instanceof VolatileLabelMultisetType ) { return ( org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter< T > ) new HighlightingStreamConverterLabelMultisetType( stream ); }

		return null;

	}

}
