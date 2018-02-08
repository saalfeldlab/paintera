package bdv.bigcat.viewer.atlas.source;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import bdv.bigcat.composite.ARGBCompositeAlphaAdd;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.viewer.ARGBColorConverter;
import bdv.bigcat.viewer.atlas.CurrentModeConverter;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.Source;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.util.StringConverter;
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
							final AtlasSourceState< ?, ? > state = info.getState( source );
							final String name = state.nameProperty().get();
							final TitledPane sourceElement = new TitledPane();
							sourceElement.setText( null );
							if ( !expanded.containsKey( source ) )
								expanded.put( source, false );
							sourceElement.setExpanded( expanded.get( source ) );
							sourceElement.expandedProperty().addListener( ( obs, oldv, newv ) -> expanded.put( source, newv ) );

							final Node closeButton = CloseButton.create( 8 );
							closeButton.setOnMousePressed( event -> removeDialog( this.remove, source ) );
							final Label sourceElementLabel = new Label( name, closeButton );
							sourceElementLabel.textProperty().bind( state.nameProperty() );
							sourceElementLabel.setOnMouseClicked( event -> {
								if ( event.getClickCount() != 2 )
									return;
								event.consume();
								final Dialog< Boolean > d = new Dialog<>();
								d.setTitle( "Set source name" );
								final TextField tf = new TextField( state.nameProperty().get() );
								tf.setPromptText( "source name" );
								d.getDialogPane().getButtonTypes().addAll( ButtonType.OK, ButtonType.CANCEL );
								d.getDialogPane().lookupButton( ButtonType.OK ).disableProperty().bind( tf.textProperty().isNull().or( tf.textProperty().length().isEqualTo( 0 ) ) );
								d.setGraphic( tf );
								d.setResultConverter( ButtonType.OK::equals );
								final Optional< Boolean > result = d.showAndWait();
								if ( result.isPresent() && result.get() )
									state.nameProperty().set( tf.getText() );
							} );
							sourceElementLabel.setContentDisplay( ContentDisplay.RIGHT );
							sourceElementLabel.underlineProperty().bind( info.isCurrentSource( source ) );

							final HBox sourceElementButtons = getPaneGraphics( state );
							sourceElementButtons.setMaxWidth( Double.MAX_VALUE );
							HBox.setHgrow( sourceElementButtons, Priority.ALWAYS );
							final HBox graphic = new HBox( sourceElementButtons, sourceElementLabel );
							graphic.setSpacing( 20 );
							graphic.prefWidthProperty().bind( this.width.multiply( 0.8 ) );
							sourceElement.setGraphic( graphic );
							addDragAndDropListener( sourceElement, this.info, contents.getChildren() );

							sourceElement.setContent( getPaneContents( state ) );
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

		Optional.ofNullable( compositeInfo( state.compositeProperty() ) ).ifPresent( node -> info.getChildren().add( node ) );
		Optional.ofNullable( converterInfo( conv ) ).ifPresent( node -> info.getChildren().add( node ) );

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

	private static Node compositeInfo(
			final ObjectProperty< Composite< ARGBType, ARGBType > > composite )
	{
		final String ALPHA_ADD = "alpha add";
		final String ALPHA_YCBCR = "alpha YCbCr";
		final String COPY = "copy";
		final ObservableList< String > availableComposites = FXCollections.observableArrayList( ALPHA_YCBCR, ALPHA_ADD, COPY );
		final StringConverter< Composite< ARGBType, ARGBType > > converter = new StringConverter< Composite< ARGBType, ARGBType > >()
		{

			@Override
			public String toString( final Composite< ARGBType, ARGBType > object )
			{
				return object instanceof ARGBCompositeAlphaAdd ? ALPHA_ADD : object instanceof CompositeCopy ? COPY : ALPHA_YCBCR;
			}

			@Override
			public Composite< ARGBType, ARGBType > fromString( final String string )
			{
				switch ( string )
				{
				case ALPHA_ADD:
					return new ARGBCompositeAlphaAdd();
				case ALPHA_YCBCR:
					return new ARGBCompositeAlphaYCbCr();
				case COPY:
					return new CompositeCopy<>();
				default:
					return null;
				}
			}
		};
		return compositeInfo( composite, availableComposites, converter );
	}

	private static Node compositeInfo(
			final ObjectProperty< Composite< ARGBType, ARGBType > > composite,
			final ObservableList< String > availableComposites,
			final StringConverter< Composite< ARGBType, ARGBType > > converter )
	{
		final ComboBox< String > combo = new ComboBox<>( availableComposites );
		combo.setValue( converter.toString( composite.get() ) );
		Bindings.bindBidirectional( combo.valueProperty(), composite, converter );
		final TitledPane tp = new TitledPane( "Composite", combo );
		return tp;
	}

	private static Node converterInfo( final Converter< ?, ? > converter )
	{
		final TitledPane tp = new TitledPane( "Converter", null );
//		tp.setExpanded( false );
		if ( converter instanceof ARGBColorConverter< ? > )
		{

			final ARGBColorConverter< ? > conv = ( ARGBColorConverter< ? > ) converter;

			final TilePane tilePane = new TilePane( Orientation.VERTICAL );
			tilePane.setMinWidth( 0 );

			final ObjectProperty< ARGBType > cProp = conv.colorProperty();
			final int c = cProp.get().get();
			final ColorPicker picker = new ColorPicker( Color.rgb( ARGBType.red( c ), ARGBType.green( c ), ARGBType.blue( c ), ARGBType.alpha( c ) / 255.0 ) );
			picker.valueProperty().addListener( ( obs, oldv, newv ) -> cProp.set( toARGBType( newv ) ) );
			final HBox colorPickerBox = new HBox( picker );
			HBox.setHgrow( picker, Priority.ALWAYS );
			tilePane.getChildren().add( colorPickerBox );

			final StringBinding min = conv.minProperty().asString();
			final StringBinding max = conv.maxProperty().asString();
			final TextField minInput = new TextField( min.get() );
			final TextField maxInput = new TextField( max.get() );
			minInput.promptTextProperty().bind( conv.minProperty().asString( "min=%f" ) );
			minInput.promptTextProperty().bind( conv.maxProperty().asString( "max=%f" ) );

			min.addListener( ( obs, oldv, newv ) -> minInput.setText( newv ) );
			max.addListener( ( obs, oldv, newv ) -> maxInput.setText( newv ) );

			final TextFormatter< Double > minFormatter = DoubleStringFormatter.createFormatter( conv.minProperty().get(), 2 );
			final TextFormatter< Double > maxFormatter = DoubleStringFormatter.createFormatter( conv.maxProperty().get(), 2 );

			minInput.setTextFormatter( minFormatter );
			maxInput.setTextFormatter( maxFormatter );
			minInput.setOnKeyPressed( event -> {
				if ( event.getCode().equals( KeyCode.ENTER ) )
				{
					minInput.commitValue();
					event.consume();
				}
			} );
			maxInput.setOnKeyPressed( event -> {
				if ( event.getCode().equals( KeyCode.ENTER ) )
				{
					maxInput.commitValue();
					event.consume();
				}
			} );

			minInput.setTooltip( new Tooltip( "min" ) );
			maxInput.setTooltip( new Tooltip( "max" ) );

			minFormatter.valueProperty().addListener( ( obs, oldv, newv ) -> conv.minProperty().set( newv ) );
			maxFormatter.valueProperty().addListener( ( obs, oldv, newv ) -> conv.maxProperty().set( newv ) );

			conv.minProperty().addListener( ( obs, oldv, newv ) -> minFormatter.setValue( newv.doubleValue() ) );
			conv.maxProperty().addListener( ( obs, oldv, newv ) -> maxFormatter.setValue( newv.doubleValue() ) );

			final HBox minMaxBox = new HBox( minInput, maxInput );
			tilePane.getChildren().add( minMaxBox );

			final NumericSliderWithField alphaSliderWithField = new NumericSliderWithField( 0, 1, conv.alphaProperty().get() );
			alphaSliderWithField.slider().valueProperty().bindBidirectional( conv.alphaProperty() );
			alphaSliderWithField.textField().setMinWidth( 48 );
			alphaSliderWithField.textField().setMaxWidth( 48 );
			final HBox alphaBox = new HBox( alphaSliderWithField.slider(), alphaSliderWithField.textField() );
			Tooltip.install( alphaSliderWithField.slider(), new Tooltip( "alpha" ) );
			HBox.setHgrow( alphaSliderWithField.slider(), Priority.ALWAYS );
			tilePane.getChildren().add( alphaBox );

			tp.setContent( tilePane );
		}

		else if ( converter instanceof CurrentModeConverter< ?, ? > )
		{
			final CurrentModeConverter< ?, ? > conv = ( CurrentModeConverter< ?, ? > ) converter;
			final VBox contents = new VBox();
			final GridPane gp = new GridPane();
			final ColumnConstraints secondColumnConstraints = new ColumnConstraints();
			secondColumnConstraints.setMaxWidth( Double.MAX_VALUE );
			secondColumnConstraints.setHgrow( Priority.ALWAYS );
			gp.getColumnConstraints().addAll( secondColumnConstraints );

			final int textFieldWidth = 60;
			int row = 0;

			contents.getChildren().add( gp );

			{
				final Slider alphaSlider = new Slider( 0, 1, conv.alphaProperty().get() );
				alphaSlider.valueProperty().bindBidirectional( conv.alphaProperty() );
				alphaSlider.setShowTickLabels( true );
				alphaSlider.setTooltip( new Tooltip( "Alpha for inactive fragments." ) );
				final TextField alphaField = new TextField();
				alphaField.textProperty().bindBidirectional( alphaSlider.valueProperty(), new NumberStringConverter() );
				alphaField.setMinWidth( textFieldWidth );
				alphaField.setMaxWidth( textFieldWidth );
				gp.add( alphaSlider, 0, row );
				gp.add( alphaField, 1, row );
				++row;
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
				gp.add( selectedFragmentAlphaSlider, 0, row );
				gp.add( selectedFragmentAlphaField, 1, row );
				++row;
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
				gp.add( selectedSegmentAlphaSlider, 0, row );
				gp.add( selectedSegmentAlphaField, 1, row );
				++row;
			}

			{
				final CheckBox colorFromSegmentId = new CheckBox( "Color From segment Id." );
				colorFromSegmentId.setTooltip( new Tooltip( "Generate fragment color from segment id (on) or fragment id (off)" ) );
				colorFromSegmentId.selectedProperty().bindBidirectional( conv.colorFromSegmentIdProperty() );
				contents.getChildren().add( colorFromSegmentId );
//				gp.add( colorFromSegmentId, 0, row );
//				gp.add( new Label( "Color From segment Id." ), 1, row );
//				++row;
			}

			tp.setContent( contents );
		}

		return tp;
	}

}
