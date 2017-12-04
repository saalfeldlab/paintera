package bdv.bigcat.viewer.atlas;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.TilePane;

public class SourceTabs
{

	private static final double DEFAULT_SCALE = 1.0;

	private static final double CURRENT_SOURCE_SCALE = 1.2;

	private final TilePane fp = new TilePane();

	private final Consumer< Source< ? > > remove;

	public SourceTabs(
			final ObservableList< SourceAndConverter< ? > > sources,
			final ReadOnlyIntegerProperty currentSourceIndex,
			final Consumer< Source< ? > > remove )
	{
		this.remove = remove;
		sources.addListener( ( ListChangeListener< SourceAndConverter< ? > > ) change -> {
			while ( change.next() )
			{
				final List< TitledPane > tabs = change
						.getList()
						.stream()
						.map( SourceAndConverter::getSpimSource )
						.map( source -> {
							final String name = source.getName();
							final TitledPane p = new TitledPane();
							p.setText( name );
		//							p.setContent( new Label( "expanded (source info will go here)" ) );
							p.setExpanded( false );
							final ContextMenu m = createMenu( source, remove );
							p.setContextMenu( m );
							return p;
						} )
//						.map( Label::new )
						.collect( Collectors.toList() );
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					fp.getChildren().clear();
					fp.getChildren().addAll( tabs );
				} );
			}
		} );

		currentSourceIndex.addListener( ( obs, oldv, newv ) -> {
			final ObservableList< Node > children = fp.getChildren();
			final int index = newv.intValue();
			if ( index >= 0 && index < children.size() )
				InvokeOnJavaFXApplicationThread.invoke(
						() -> IntStream.range( 0, children.size() ).forEach( i -> ( ( TitledPane ) children.get( i ) ).setUnderline( i == index ) ) );
		} );

	}

	public Node getTabs()
	{
		return fp;
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

}
