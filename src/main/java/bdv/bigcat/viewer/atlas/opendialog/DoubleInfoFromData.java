package bdv.bigcat.viewer.atlas.opendialog;

import java.util.HashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

final class DoubleInfoFromData
{

	private static final int maxLength = 5;

	private final DoubleProperty[] data = Stream.generate( SimpleDoubleProperty::new ).limit( maxLength ).toArray( DoubleProperty[]::new );

	private final HashMap< DoubleProperty, DoubleProperty > bindings = new HashMap<>();

	public void set( final double[] data )
	{
		IntStream.range( 0, data.length ).forEach( i -> this.data[ i ].set( data[ i ] ) );
	}

	public void bind( final int[] lookup, final DoubleProperty... toBind )
	{
		unbind();
		for ( int i = 0; i < toBind.length; ++i )
		{
			final DoubleProperty b1 = data[ lookup[ i ] ];
			final DoubleProperty b2 = toBind[ i ];
			b2.set( b1.get() );
			Bindings.bindBidirectional( b1, b2 );
			this.bindings.put( b1, b2 );
		}
	}

	public void unbind()
	{
		this.bindings.forEach( Bindings::unbindBidirectional );
		this.bindings.clear();
	}

}
