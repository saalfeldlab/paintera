package bdv.bigcat.viewer.atlas.opendialog.meta;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import bdv.bigcat.viewer.atlas.opendialog.AxisOrder;
import bdv.bigcat.viewer.atlas.opendialog.OpenSourceDialog;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MetaPanel
{

	private static final double GRID_HGAP = 0;

	private static final double TEXTFIELD_WIDTH = 100;

	private static final String X_STRING = "X";

	private static final String Y_STRING = "Y";

	private static final String Z_STRING = "Z";

	private final ObjectProperty< AxisOrder > defaultAxisOrder = new SimpleObjectProperty<>();

	private final ObservableList< AxisOrder > compatibleOrders = FXCollections.observableArrayList();

	private final ComboBox< AxisOrder > axisOrderChoice = new ComboBox<>( compatibleOrders );

	private final SpatialInformation resolution = new SpatialInformation( TEXTFIELD_WIDTH, X_STRING, Y_STRING, Z_STRING );

	private final SpatialInformation offset = new SpatialInformation( TEXTFIELD_WIDTH, X_STRING, Y_STRING, Z_STRING );

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

		final GridPane spatialInfo = new GridPane();
		spatialInfo.setHgap( GRID_HGAP );
		addToGrid( spatialInfo, 0, 0, new Label( "Resolution " ), resolution.textX(), resolution.textY(), resolution.textZ() );
		addToGrid( spatialInfo, 0, 1, new Label( "Offset" ), offset.textX(), offset.textY(), offset.textZ() );
		final ColumnConstraints cc = new ColumnConstraints();
		cc.setHgrow( Priority.ALWAYS );
		spatialInfo.getColumnConstraints().addAll( cc );

		this.axisOrderChoice.setPrefWidth( TEXTFIELD_WIDTH );
		this.defaultAxisOrder.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
			{
				this.compatibleOrders.setAll(
						Arrays
								.stream( AxisOrder.values() )
								.filter( ao -> ao.numDimensions() == newv.numDimensions() )
								.collect( Collectors.toList() ) );
				this.axisOrderChoice.setValue( newv );
			}
		} );
		this.axisOrderChoice.setPromptText( "Axis Order" );
		addToGrid( spatialInfo, 0, 2, new Label( "Axis Order" ) );
		addToGrid( spatialInfo, 3, 2, this.axisOrderChoice );

		content.getChildren().add( spatialInfo );

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

	public ObjectProperty< AxisOrder > axisOrderProperty()
	{
		return this.axisOrderChoice.valueProperty();
	}

	public ObjectProperty< AxisOrder > defaultAxisOrderProperty()
	{
		return this.defaultAxisOrder;
	}

	public AxisOrder getAxisOrder()
	{
		return this.axisOrderChoice.valueProperty().get();
	}

	public void listenOnResolution( final DoubleProperty x, final DoubleProperty y, final DoubleProperty z )
	{
		this.resolution.bindTo( this.axisOrderChoice.valueProperty(), x, y, z );
	}

	public void listenOnOffset( final DoubleProperty x, final DoubleProperty y, final DoubleProperty z )
	{
		this.offset.bindTo( this.axisOrderChoice.valueProperty(), x, y, z );
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
		return asArray( resolution.textX().textProperty(), resolution.textY().textProperty(), resolution.textZ().textProperty() );
	}

	public double[] getOffset()
	{
		return asArray( offset.textX().textProperty(), offset.textY().textProperty(), offset.textZ().textProperty() );
	}

	public double[] asArray( final ObservableStringValue... values )
	{
		return Arrays.stream( values ).map( ObservableValue::getValue ).mapToDouble( Double::parseDouble ).toArray();
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

	private static void addToGrid( final GridPane grid, final int startCol, final int row, final Node... nodes )
	{
		for ( int col = startCol, i = 0; i < nodes.length; ++i, ++col )
			grid.add( nodes[ i ], col, row );
	}

}
