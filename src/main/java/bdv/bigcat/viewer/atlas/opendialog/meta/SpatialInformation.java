package bdv.bigcat.viewer.atlas.opendialog.meta;

import java.util.HashMap;
import java.util.Optional;

import bdv.bigcat.viewer.atlas.opendialog.AxisOrder;
import bdv.bigcat.viewer.atlas.opendialog.meta.MetaPanel.DoubleFilter;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

public class SpatialInformation
{

	private DoubleProperty x = new SimpleDoubleProperty();

	private DoubleProperty y = new SimpleDoubleProperty();

	private DoubleProperty z = new SimpleDoubleProperty();

	private final DoubleProperty xPermuted = new SimpleDoubleProperty();

	private final DoubleProperty yPermuted = new SimpleDoubleProperty();

	private final DoubleProperty zPermuted = new SimpleDoubleProperty();

	private final TextField textX = new TextField( "" );

	private final TextField textY = new TextField( "" );

	private final TextField textZ = new TextField( "" );

	private final SimpleObjectProperty< AxisOrder > axisOrder = new SimpleObjectProperty<>();

	private final HashMap< DoubleProperty, DoubleProperty > bindings = new HashMap<>();

	public SpatialInformation( final double textFieldWidth, final String promptTextX, final String promptTextY, final String promptTextZ )
	{
		textX.setPrefWidth( textFieldWidth );
		textY.setPrefWidth( textFieldWidth );
		textZ.setPrefWidth( textFieldWidth );

		textX.setPromptText( promptTextX );
		textY.setPromptText( promptTextY );
		textZ.setPromptText( promptTextZ );

		textX.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		textY.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		textZ.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );

		this.textX.textProperty().bindBidirectional( xPermuted, new Converter() );
		this.textY.textProperty().bindBidirectional( yPermuted, new Converter() );
		this.textZ.textProperty().bindBidirectional( zPermuted, new Converter() );

		this.axisOrder.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
				updateBindings( newv );
		} );

	}

	public TextField textX()
	{
		return this.textX;
	}

	public TextField textY()
	{
		return this.textY;
	}

	public TextField textZ()
	{
		return this.textZ;
	}

	public void bindTo( final ObservableValue< AxisOrder > axisOrder, final DoubleProperty x, final DoubleProperty y, final DoubleProperty z )
	{
		this.x = x;
		this.y = y;
		this.z = z;
		updateBindings( Optional.ofNullable( axisOrder.getValue() ).map( AxisOrder::spatialOnly ).orElse( AxisOrder.XYZ ) );
		this.axisOrder.bind( Bindings.createObjectBinding( () -> Optional.ofNullable( axisOrder.getValue() ).map( AxisOrder::spatialOnly ).orElse( AxisOrder.XYZ ), axisOrder ) );

	}

	private void updateBindings( final AxisOrder axisOrder )
	{
		final DoubleProperty[] xyz = new DoubleProperty[] { x, y, z };
		final int[] p = invertPermutation( Optional.ofNullable( axisOrder ).orElse( AxisOrder.XYZ ).permutation() );
		this.bindings.forEach( ( prop1, prop2 ) -> prop1.unbindBidirectional( prop2 ) );
		xPermuted.bindBidirectional( xyz[ p[ 0 ] ] );
		yPermuted.bindBidirectional( xyz[ p[ 1 ] ] );
		zPermuted.bindBidirectional( xyz[ p[ 2 ] ] );

		this.bindings.put( xPermuted, xyz[ p[ 0 ] ] );
		this.bindings.put( yPermuted, xyz[ p[ 1 ] ] );
		this.bindings.put( zPermuted, xyz[ p[ 2 ] ] );
	}

	private static double parseDouble( final String s )
	{
		return s.length() == 0 ? Double.NaN : Double.parseDouble( s );
	}

	// TODO create own converter because I wasted hours of trying to find out
	// why some of my inputs wouldn't come through (it would work for resolution
	// but not offset). It turns out that the default constructed
	// NumberStringConverter would ignore any values >= 1000. So much time
	// wasted!!
	private static class Converter extends StringConverter< Number >
	{

		@Override
		public String toString( final Number object )
		{
			return object.toString();
		}

		@Override
		public Number fromString( final String string )
		{
			return parseDouble( string );
		}

	}

	private static int[] invertPermutation( final int[] permutation )
	{
		final int[] inverse = new int[ permutation.length ];
		for ( int i = 0; i < inverse.length; ++i )
			inverse[ permutation[ i ] ] = i;
		return inverse;
	}

}
