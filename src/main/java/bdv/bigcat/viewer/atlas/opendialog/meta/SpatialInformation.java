package bdv.bigcat.viewer.atlas.opendialog.meta;

import bdv.bigcat.viewer.atlas.opendialog.meta.MetaPanel.DoubleFilter;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

public class SpatialInformation
{

	private final DoubleProperty x = new SimpleDoubleProperty();

	private final DoubleProperty y = new SimpleDoubleProperty();

	private final DoubleProperty z = new SimpleDoubleProperty();

	private final TextField textX = new TextField( "" );

	private final TextField textY = new TextField( "" );

	private final TextField textZ = new TextField( "" );

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

		this.textX.textProperty().bindBidirectional( x, new Converter() );
		this.textY.textProperty().bindBidirectional( y, new Converter() );
		this.textZ.textProperty().bindBidirectional( z, new Converter() );

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

	public void bindTo( final DoubleProperty x, final DoubleProperty y, final DoubleProperty z )
	{
		this.x.bindBidirectional( x );
		this.y.bindBidirectional( y );
		this.z.bindBidirectional( z );

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

}
