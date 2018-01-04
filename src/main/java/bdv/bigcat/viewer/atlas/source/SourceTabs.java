package bdv.bigcat.viewer.atlas.source;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.sun.javafx.scene.control.skin.CustomColorDialog;

import bdv.bigcat.viewer.atlas.source.AtlasSourceState.RawSourceState;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.Source;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.TilePane;
import javafx.scene.shape.Rectangle;

public class SourceTabs
{

	private static final double DEFAULT_SCALE = 1.0;

	private static final double CURRENT_SOURCE_SCALE = 1.2;

	private final TilePane fp = new TilePane();

	private final Consumer< Source< ? > > remove;

	private final SourceInfo info;

	private final HashMap< Source< ? >, Boolean > expanded = new HashMap<>();

	public SourceTabs(
			final ObservableIntegerValue currentSourceIndex,
			final Consumer< Source< ? > > remove,
			final SourceInfo info )
	{
		this.remove = remove;
		this.info = info;
		this.info.trackSources().addListener( ( ListChangeListener< Source< ? > > ) change -> {
			while ( change.next() )
			{
				final List< TitledPane > tabs = change
						.getList()
						.stream()
						.map( source -> {
							final String name = source.getName();
							final TitledPane p = new TitledPane();
							p.setText( name );
							// p.setContent( new Label( "expanded (source info
							// will go here)" ) );
							if ( !expanded.containsKey( source ) )
								expanded.put( source, false );
							p.setExpanded( expanded.get( source ) );
							final ContextMenu m = createMenu( source, this.remove );
							p.setContextMenu( m );
							p.setGraphic( getPaneGraphics( info.getState( source ) ) );
							addDragAndDropListener( p, this.info, fp.getChildren() );
							return p;
						} )
//						.map( Label::new )
						.collect( Collectors.toList() );
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					fp.getChildren().clear();
					fp.getChildren().addAll( tabs );
					underlineActive( fp, currentSourceIndex.intValue() );
				} );
			}
		} );

		currentSourceIndex.addListener( ( obs, oldv, newv ) -> underlineActive( fp, newv.intValue() ) );

	}

	private static void underlineActive( final TilePane pane, final int index )
	{
		final List< TitledPane > children = pane.getChildren().stream().filter( p -> p instanceof TitledPane ).map( p -> ( TitledPane ) p ).collect( Collectors.toList() );
		IntStream.range( 0, children.size() ).forEach( i -> children.get( i ).setUnderline( i == index ) );
	}

	public Node getTabs()
	{
		return fp;
	}

	public void setOrientation( final Orientation orientation )
	{
		this.fp.setOrientation( orientation );
	}

	private static ContextMenu createMenu( final Source< ? > source, final Consumer< Source< ? > > remove )
	{
		final ContextMenu menu = new ContextMenu();
		final MenuItem removeItem = new MenuItem();
		removeItem.setText( "Remove" );
		removeItem.setOnAction( a -> remove.accept( source ) );
		menu.getItems().add( removeItem );
		return menu;
	}

	@SuppressWarnings( "restriction" )
	private static Node getPaneGraphics( final AtlasSourceState< ?, ? > state )
	{
		final CheckBox cb = new CheckBox();
		cb.selectedProperty().bindBidirectional( state.visibleProperty() );
		cb.selectedProperty().set( state.visibleProperty().get() );
		final TilePane tp = new TilePane( cb );
		if ( state instanceof RawSourceState< ?, ? > )
		{
			final RawSourceState< ?, ? > rawState = ( RawSourceState< ?, ? > ) state;
			final Rectangle rect = new Rectangle( 15.0, 15.0 );
			rect.fillProperty().bind( rawState.colorProperty() );
			rect.setOnMouseClicked( event -> {
				final CustomColorDialog ccd = new CustomColorDialog( rect.getScene().getWindow() );
				ccd.setCurrentColor( rawState.colorProperty().get() );
				ccd.show();
				Optional.ofNullable( ccd.getCustomColor() ).ifPresent( c -> rawState.colorProperty().set( c ) );
			} );
			tp.getChildren().add( rect );
//			System.out.println( "ADDING RECTANGLE " + rect.getFill() + " " + rect );
		}
		return tp;
	}

	private static void addDragAndDropListener( final TitledPane p, final SourceInfo info, final List< Node > children )
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

}
