package bdv.bigcat.viewer.atlas.source;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import bdv.bigcat.viewer.atlas.source.AtlasSourceState.LabelSourceState;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState.RawSourceState;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.Source;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
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
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

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
		if ( state instanceof RawSourceState< ?, ? > )
		{
			final RawSourceState< ?, ? > rawState = ( RawSourceState< ?, ? > ) state;
			final ColorPicker picker = new ColorPicker( rawState.colorProperty().get() );
			// TODO with max width of 30, this magically hides the arrow button.
			// Hacky but works for now.
			picker.setMinWidth( 50 );
			picker.setMaxWidth( 50 );
			picker.setMaxHeight( 20 );
			picker.valueProperty().bindBidirectional( rawState.colorProperty() );
			tp.getChildren().add( picker );
		}
		return tp;
	}

	private static Node getPaneContents( final AtlasSourceState< ?, ? > state )
	{
		if ( state instanceof LabelSourceState< ?, ? > )
			return null;
		else if ( state instanceof RawSourceState< ?, ? > )
		{
			final RawSourceState< ?, ? > rss = ( RawSourceState< ?, ? > ) state;
			final GridPane gp = new GridPane();
			final StringBinding min = rss.minProperty().asString();
			final StringBinding max = rss.maxProperty().asString();
			final TextField minInput = new TextField( min.get() );
			final TextField maxInput = new TextField( max.get() );
			minInput.promptTextProperty().bind( rss.minProperty().asString( "min=%f" ) );
			minInput.promptTextProperty().bind( rss.maxProperty().asString( "max=%f" ) );

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
				Optional.ofNullable( minInput.getText() ).map( Double::parseDouble ).ifPresent( d -> rss.minProperty().set( d ) );
				Optional.ofNullable( maxInput.getText() ).map( Double::parseDouble ).ifPresent( d -> rss.maxProperty().set( d ) );
			} );

			GridPane.setHgrow( requestSettingMinMax, Priority.ALWAYS );

			gp.add( minInput, 0, 0 );
			gp.add( maxInput, 1, 0 );
			gp.add( requestSettingMinMax, 2, 0 );

			return gp;
		}
		else
			return null;
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

}
