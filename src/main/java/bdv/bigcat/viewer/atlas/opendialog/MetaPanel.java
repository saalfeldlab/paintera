package bdv.bigcat.viewer.atlas.opendialog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.UnaryOperator;

import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MetaPanel
{

	private static final double GRID_HGAP = 0;

	private static final double TEXTFIELD_WIDTH = 80;

	private static final String X_STRING = "X";

	private static final String Y_STRING = "Y";

	private static final String Z_STRING = "Z";

	private final TextField resX = new TextField( "" );

	private final TextField resY = new TextField( "" );

	private final TextField resZ = new TextField( "" );

	private final TextField offX = new TextField( "" );

	private final TextField offY = new TextField( "" );

	private final TextField offZ = new TextField( "" );

	private final TextField min = new TextField( "" );

	private final TextField max = new TextField( "" );

	private final VBox content = new VBox();

	private final ScrollPane cc = new ScrollPane( content );

	private final TitledPane pane = new TitledPane( "meta", cc );

	private final VBox rawMeta = new VBox();

	private final VBox labelMeta = new VBox();

	private final HashSet< Node > additionalMeta = new HashSet<>();

	private final SimpleObjectProperty< OpenSourceDialog.TYPE > dataType = new SimpleObjectProperty<>( null );

	public MetaPanel()
	{
		cc.setFitToWidth( true );

		final GridPane resolutionAndOffset = new GridPane();
		resolutionAndOffset.setHgap( GRID_HGAP );
		final Label resolutionLabel = new Label( "resolution" );
		final Label offsetLabel = new Label( "offset" );
		resolutionAndOffset.add( resolutionLabel, 0, 0 );
		resolutionAndOffset.add( offsetLabel, 0, 1 );
		resolutionAndOffset.add( resX, 1, 0 );
		resolutionAndOffset.add( resY, 2, 0 );
		resolutionAndOffset.add( resZ, 3, 0 );
		resolutionAndOffset.add( offX, 1, 1 );
		resolutionAndOffset.add( offY, 2, 1 );
		resolutionAndOffset.add( offZ, 3, 1 );

		resX.setPrefWidth( TEXTFIELD_WIDTH );
		resY.setPrefWidth( TEXTFIELD_WIDTH );
		resZ.setPrefWidth( TEXTFIELD_WIDTH );
		offX.setPrefWidth( TEXTFIELD_WIDTH );
		offY.setPrefWidth( TEXTFIELD_WIDTH );
		offZ.setPrefWidth( TEXTFIELD_WIDTH );

		resX.setPromptText( X_STRING );
		resY.setPromptText( Y_STRING );
		resZ.setPromptText( Z_STRING );
		offX.setPromptText( X_STRING );
		offY.setPromptText( Y_STRING );
		offZ.setPromptText( Z_STRING );

		final ColumnConstraints cc = new ColumnConstraints();
		cc.setHgrow( Priority.ALWAYS );
		resolutionAndOffset.getColumnConstraints().addAll( cc );

		resX.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		resY.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		resZ.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );

		offX.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		offY.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		offZ.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		content.getChildren().add( resolutionAndOffset );

		this.dataType.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					final ObservableList< Node > children = this.content.getChildren();
					children.removeAll( this.additionalMeta );
					this.additionalMeta.clear();
					switch ( newv )
					{
					case RAW:
						children.add( this.rawMeta );
						this.additionalMeta.add( this.rawMeta );
						break;
					case LABEL:
						children.add( this.labelMeta );
						this.additionalMeta.add( this.labelMeta );
						break;
					default:
						break;
					}
				} );
		} );

		final GridPane rawMinMax = new GridPane();
		rawMinMax.getColumnConstraints().add( cc );
		rawMinMax.add( new Label( "Intensity Range" ), 0, 0 );
		rawMinMax.add( this.min, 1, 0 );
		rawMinMax.add( this.max, 2, 0 );
		this.min.setPromptText( "min" );
		this.max.setPromptText( "max" );
		this.min.setPrefWidth( TEXTFIELD_WIDTH );
		this.max.setPrefWidth( TEXTFIELD_WIDTH );
		this.rawMeta.getChildren().add( rawMinMax );

	}

	public void listenOnResolution( final DoubleProperty resX, final DoubleProperty resY, final DoubleProperty resZ )
	{
		resX.addListener( ( obsRes, oldRes, newRes ) -> {
			if ( Double.isFinite( newRes.doubleValue() ) )
				this.resX.setText( newRes.toString() );
		} );

		resY.addListener( ( obsRes, oldRes, newRes ) -> {
			if ( Double.isFinite( newRes.doubleValue() ) )
				this.resY.setText( newRes.toString() );
		} );

		resZ.addListener( ( obsRes, oldRes, newRes ) -> {
			if ( Double.isFinite( newRes.doubleValue() ) )
				this.resZ.setText( newRes.toString() );
		} );
	}

	public void listenOnOffset( final DoubleProperty offX, final DoubleProperty offY, final DoubleProperty offZ )
	{
		offX.addListener( ( obsOff, oldOff, newOff ) -> {
			if ( Double.isFinite( newOff.doubleValue() ) )
				this.offX.setText( newOff.toString() );
		} );

		offY.addListener( ( obsOff, oldOff, newOff ) -> {
			if ( Double.isFinite( newOff.doubleValue() ) )
				this.offY.setText( newOff.toString() );
		} );

		offZ.addListener( ( obsOff, oldOff, newOff ) -> {
			if ( Double.isFinite( newOff.doubleValue() ) )
				this.offZ.setText( newOff.toString() );
		} );
	}

	public void listenOnMinMax( final DoubleProperty min, final DoubleProperty max )
	{
		min.addListener( ( obs, oldv, newv ) -> {
			if ( Double.isFinite( newv.doubleValue() ) )
				this.min.setText( Double.toString( newv.doubleValue() ) );
		} );

		max.addListener( ( obs, oldv, newv ) -> {
			if ( Double.isFinite( newv.doubleValue() ) )
				this.max.setText( Double.toString( newv.doubleValue() ) );
		} );
	}

	public Node getPane()
	{
		return pane;
	}

	public static class DoubleFilter implements UnaryOperator< Change >
	{

		@Override
		public Change apply( final Change t )
		{
			final String input = t.getText();
			return input.matches( "\\d*(\\.\\d*)?" ) ? t : null;
		}
	}

	public double[] getResolution()
	{
		return Arrays
				.asList( resX, resY, resZ )
				.stream()
				.map( res -> res.getText() )
				.mapToDouble( text -> text.length() > 0 ? Double.parseDouble( text ) : 1.0 )
				.toArray();
	}

	public double[] getOffset()
	{
		return Arrays
				.asList( offX, offY, offZ )
				.stream()
				.map( res -> res.getText() )
				.mapToDouble( text -> text.length() > 0 ? Double.parseDouble( text ) : 0.0 )
				.toArray();
	}

	public double min()
	{
		final String text = min.getText();
		return text.length() > 0 ? Double.parseDouble( min.getText() ) : Double.NaN;
	}

	public double max()
	{
		final String text = max.getText();
		return text.length() > 0 ? Double.parseDouble( max.getText() ) : Double.NaN;
	}

	public void bindDataTypeTo( final ObjectProperty< OpenSourceDialog.TYPE > dataType )
	{
		this.dataType.bind( dataType );
	}

}
