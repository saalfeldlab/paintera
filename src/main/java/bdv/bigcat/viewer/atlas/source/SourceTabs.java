package bdv.bigcat.viewer.atlas.source;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import bdv.bigcat.viewer.ARGBColorConverter;
import bdv.bigcat.viewer.atlas.CurrentModeConverter;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.Source;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.util.converter.NumberStringConverter;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class SourceTabs
{

	private final VBox contents = new VBox();
	{
		contents.setSpacing( 0 );
		contents.setMaxHeight( Double.MAX_VALUE );
	}

	private final ScrollPane sp = new ScrollPane( contents );
	{
		sp.setMaxWidth( Double.MAX_VALUE );
		sp.setHbarPolicy( ScrollBarPolicy.NEVER );
		sp.setVbarPolicy( ScrollBarPolicy.AS_NEEDED );
	}

	private final Consumer< Source< ? > > remove;

	private final SourceInfo info;

	private final HashMap< Source< ? >, Boolean > expanded = new HashMap<>();

	private final DoubleProperty width = new SimpleDoubleProperty();

	public SourceTabs(
			final ObservableIntegerValue currentSourceIndex,
			final Consumer< Source< ? > > remove,
			final SourceInfo info )
	{
		this.remove = source -> {
			remove.accept( source );
			expanded.remove( source );
		};
		this.info = info;
		width.set( 200 );
		this.info.trackSources().addListener( ( ListChangeListener< Source< ? > > ) change -> {
			while ( change.next() )
			{
				final List< Node > tabs = change
						.getList()
						.stream()
						.map( source -> {
							final String name = source.getName();
							final TitledPane sourceElement = new TitledPane();
							sourceElement.setText( null );
							if ( !expanded.containsKey( source ) )
								expanded.put( source, false );
							sourceElement.setExpanded( expanded.get( source ) );
							sourceElement.expandedProperty().addListener( ( obs, oldv, newv ) -> expanded.put( source, newv ) );

							final Node closeButton = CloseButton.create( 8 );
							closeButton.setOnMousePressed( event -> removeDialog( this.remove, source ) );
							final Label sourceElementLabel = new Label( name, closeButton );
							sourceElementLabel.setContentDisplay( ContentDisplay.RIGHT );
							sourceElementLabel.underlineProperty().bind( info.isCurrentSource( source ) );

							final HBox sourceElementButtons = getPaneGraphics( info.getState( source ) );
							sourceElementButtons.setMaxWidth( Double.MAX_VALUE );
							HBox.setHgrow( sourceElementButtons, Priority.ALWAYS );
							final HBox graphic = new HBox( sourceElementButtons, sourceElementLabel );
							graphic.setSpacing( 20 );
							graphic.prefWidthProperty().bind( this.width.multiply( 0.8 ) );
							sourceElement.setGraphic( graphic );
							addDragAndDropListener( sourceElement, this.info, contents.getChildren() );

							sourceElement.setContent( getPaneContents( info.getState( source ) ) );
							sourceElement.maxWidthProperty().bind( width );

							return sourceElement;
						} )
						.collect( Collectors.toList() );
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					contents.getChildren().clear();
					contents.getChildren().addAll( tabs );
				} );
			}
		} );
	}

	public Node getTabs()
	{
		return sp;
	}

	public DoubleProperty widthProperty()
	{
		return this.width;
	}

	private static HBox getPaneGraphics( final AtlasSourceState< ?, ? > state )
	{
		final CheckBox cb = new CheckBox();
		cb.setMaxWidth( 20 );
		cb.selectedProperty().bindBidirectional( state.visibleProperty() );
		cb.selectedProperty().set( state.visibleProperty().get() );
		final HBox tp = new HBox( cb );
		return tp;
	}

	private static Node getPaneContents( final AtlasSourceState< ?, ? > state )
	{
		final Converter< ?, ARGBType > conv = state.converterProperty().get();
		final VBox info = new VBox();

		Optional.ofNullable( converterInfo( conv ) ).ifPresent( node -> info.getChildren().add( node ) );
//		switch ( state.typeProperty().get() )
//		{
//		case LABEL:
//			return null;
//		case RAW:
//			final GridPane gp = new GridPane();
//			if ( conv instanceof ARGBColorConverter< ? > )
//			{
//				final ARGBColorConverter< ? > cconv = ( ARGBColorConverter< ? > ) conv;
//				final StringBinding min = cconv.minProperty().asString();
//				final StringBinding max = cconv.maxProperty().asString();
//				final TextField minInput = new TextField( min.get() );
//				final TextField maxInput = new TextField( max.get() );
//				minInput.promptTextProperty().bind( cconv.minProperty().asString( "min=%f" ) );
//				minInput.promptTextProperty().bind( cconv.maxProperty().asString( "max=%f" ) );
//
//				min.addListener( ( obs, oldv, newv ) -> minInput.setText( newv ) );
//				max.addListener( ( obs, oldv, newv ) -> maxInput.setText( newv ) );
//
//				final Pattern pattern = Pattern.compile( "\\d*|\\d+\\.\\d*|\\d*\\.\\d+" );
//				final TextFormatter< Double > minFormatter = new TextFormatter<>( ( UnaryOperator< TextFormatter.Change > ) change -> {
//					return pattern.matcher( change.getControlNewText() ).matches() ? change : null;
//				} );
//				final TextFormatter< Double > maxFormatter = new TextFormatter<>( ( UnaryOperator< TextFormatter.Change > ) change -> {
//					return pattern.matcher( change.getControlNewText() ).matches() ? change : null;
//				} );
//
//				minInput.setTextFormatter( minFormatter );
//				maxInput.setTextFormatter( maxFormatter );
//
//				final Button requestSettingMinMax = new Button( "set contrast" );
//				requestSettingMinMax.setOnAction( event -> {
//					Optional.ofNullable( minInput.getText() ).map( Double::parseDouble ).ifPresent( d -> cconv.minProperty().set( d ) );
//					Optional.ofNullable( maxInput.getText() ).map( Double::parseDouble ).ifPresent( d -> cconv.maxProperty().set( d ) );
//				} );
//
//				GridPane.setHgrow( requestSettingMinMax, Priority.ALWAYS );
//
//				gp.add( minInput, 0, 0 );
//				gp.add( maxInput, 1, 0 );
//				gp.add( requestSettingMinMax, 2, 0 );
//			}
//
//			return gp;
//		default:
//			return null;
//		}
		return info;
	}

	private static void addDragAndDropListener( final Node p, final SourceInfo info, final List< Node > children )
	{
		p.setOnDragDetected( event -> {
			p.startFullDrag();
		} );

		p.setOnMouseDragReleased( event -> {
			final Object origin = event.getGestureSource();
			if ( origin != p && origin instanceof TitledPane )
			{
				final TitledPane pane = ( TitledPane ) origin;
				final int sourceIndex = children.indexOf( pane );
				final int targetIndex = children.indexOf( p );
				info.moveSourceTo( sourceIndex, targetIndex );
			}
		} );
	}

	private static void removeDialog( final Consumer< Source< ? > > onRemove, final Source< ? > source )
	{
		final Alert confirmRemoval = new Alert(
				Alert.AlertType.CONFIRMATION,
				String.format( "Remove source '%s'?", source.getName() ) );
		final Button removeButton = ( Button ) confirmRemoval.getDialogPane().lookupButton(
				ButtonType.OK );
		removeButton.setText( "Remove" );
		confirmRemoval.setHeaderText( null );
		confirmRemoval.setTitle( null );
		confirmRemoval.initModality( Modality.APPLICATION_MODAL );
		final Optional< ButtonType > buttonClicked = confirmRemoval.showAndWait();
		if ( buttonClicked.orElse( ButtonType.CANCEL ).equals( ButtonType.OK ) )
			onRemove.accept( source );
	}

	private static ARGBType toARGBType( final Color color )
	{
		return new ARGBType(
				( int ) ( color.getOpacity() * 255 + 0.5 ) << 24 |
						( int ) ( color.getRed() * 255 + 0.5 ) << 16 |
						( int ) ( color.getGreen() * 255 + 0.5 ) << 8 |
						( int ) ( color.getBlue() * 255 + 0.5 ) << 0 );
	}

	private static Node converterInfo( final Converter< ?, ? > converter )
	{
		if ( converter instanceof ARGBColorConverter< ? > )
		{

			final ARGBColorConverter< ? > conv = ( ARGBColorConverter< ? > ) converter;

			final GridPane gp = new GridPane();

			final ObjectProperty< ARGBType > cProp = conv.colorProperty();
			final int c = cProp.get().get();
			final ColorPicker picker = new ColorPicker( Color.rgb( ARGBType.red( c ), ARGBType.green( c ), ARGBType.blue( c ), ARGBType.alpha( c ) / 255.0 ) );
			picker.valueProperty().addListener( ( obs, oldv, newv ) -> cProp.set( toARGBType( newv ) ) );
			gp.add( picker, 2, 0 );

			final StringBinding min = conv.minProperty().asString();
			final StringBinding max = conv.maxProperty().asString();
			final TextField minInput = new TextField( min.get() );
			final TextField maxInput = new TextField( max.get() );
			minInput.promptTextProperty().bind( conv.minProperty().asString( "min=%f" ) );
			minInput.promptTextProperty().bind( conv.maxProperty().asString( "max=%f" ) );

			min.addListener( ( obs, oldv, newv ) -> minInput.setText( newv ) );
			max.addListener( ( obs, oldv, newv ) -> maxInput.setText( newv ) );

			final Pattern pattern = Pattern.compile( "\\d*|\\d+\\.\\d*|\\d*\\.\\d+" );
			final TextFormatter< Double > minFormatter = new TextFormatter<>( ( UnaryOperator< TextFormatter.Change > ) change -> {
				return pattern.matcher( change.getControlNewText() ).matches() ? change : null;
			} );
			final TextFormatter< Double > maxFormatter = new TextFormatter<>( ( UnaryOperator< TextFormatter.Change > ) change -> {
				return pattern.matcher( change.getControlNewText() ).matches() ? change : null;
			} );

			minInput.setTextFormatter( minFormatter );
			maxInput.setTextFormatter( maxFormatter );

			final Button requestSettingMinMax = new Button( "set contrast" );
			requestSettingMinMax.setOnAction( event -> {
				Optional.ofNullable( minInput.getText() ).map( Double::parseDouble ).ifPresent( d -> conv.minProperty().set( d ) );
				Optional.ofNullable( maxInput.getText() ).map( Double::parseDouble ).ifPresent( d -> conv.maxProperty().set( d ) );
			} );

			GridPane.setHgrow( requestSettingMinMax, Priority.ALWAYS );

			gp.add( minInput, 0, 1 );
			gp.add( maxInput, 1, 1 );
			gp.add( requestSettingMinMax, 2, 1 );
			return gp;
		}

		if ( converter instanceof CurrentModeConverter< ?, ? > )
		{
			final CurrentModeConverter< ?, ? > conv = ( CurrentModeConverter< ?, ? > ) converter;
			final GridPane gp = new GridPane();
			final ColumnConstraints secondColumnConstraints = new ColumnConstraints();
			secondColumnConstraints.setMaxWidth( Double.MAX_VALUE );
			secondColumnConstraints.setHgrow( Priority.ALWAYS );
			gp.getColumnConstraints().addAll( new ColumnConstraints(), secondColumnConstraints );

			final int textFieldWidth = 60;

			{
				final Slider alphaSlider = new Slider( 0, 1, conv.alphaProperty().get() );
				alphaSlider.valueProperty().bindBidirectional( conv.alphaProperty() );
				alphaSlider.setShowTickLabels( true );
				alphaSlider.setTooltip( new Tooltip( "Alpha for inactive fragments." ) );
				final TextField alphaField = new TextField();
				alphaField.textProperty().bindBidirectional( alphaSlider.valueProperty(), new NumberStringConverter() );
				alphaField.setMinWidth( textFieldWidth );
				alphaField.setMaxWidth( textFieldWidth );
				gp.add( new Label( "alpha" ), 0, 0 );
				gp.add( alphaSlider, 1, 0 );
				gp.add( alphaField, 2, 0 );
			}

			{
				final Slider selectedFragmentAlphaSlider = new Slider( 0, 1, conv.activeFragmentAlphaProperty().get() );
				selectedFragmentAlphaSlider.valueProperty().bindBidirectional( conv.activeFragmentAlphaProperty() );
				selectedFragmentAlphaSlider.setShowTickLabels( true );
				selectedFragmentAlphaSlider.setTooltip( new Tooltip( "Alpha for selected fragments." ) );
				final TextField selectedFragmentAlphaField = new TextField();
				selectedFragmentAlphaField.textProperty().bindBidirectional( selectedFragmentAlphaSlider.valueProperty(), new NumberStringConverter() );
				selectedFragmentAlphaField.setMinWidth( textFieldWidth );
				selectedFragmentAlphaField.setMaxWidth( textFieldWidth );
				gp.add( new Label( "selected fragment alpha" ), 0, 1 );
				gp.add( selectedFragmentAlphaSlider, 1, 1 );
				gp.add( selectedFragmentAlphaField, 2, 1 );
			}

			{
				final Slider selectedSegmentAlphaSlider = new Slider( 0, 1, conv.activeSegmentAlphaProperty().get() );
				selectedSegmentAlphaSlider.valueProperty().bindBidirectional( conv.activeSegmentAlphaProperty() );
				selectedSegmentAlphaSlider.setShowTickLabels( true );
				selectedSegmentAlphaSlider.setTooltip( new Tooltip( "Alpha for active segments." ) );
				final TextField selectedSegmentAlphaField = new TextField();
				selectedSegmentAlphaField.textProperty().bindBidirectional( selectedSegmentAlphaSlider.valueProperty(), new NumberStringConverter() );
				selectedSegmentAlphaField.setMinWidth( textFieldWidth );
				selectedSegmentAlphaField.setMaxWidth( textFieldWidth );
				gp.add( new Label( "selected segment alpha" ), 0, 2 );
				gp.add( selectedSegmentAlphaSlider, 1, 2 );
				gp.add( selectedSegmentAlphaField, 2, 2 );
			}

			return gp;
		}

		return null;
	}

}
