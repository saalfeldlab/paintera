package bdv.bigcat.viewer.atlas.ui.source;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.atlas.ui.source.state.StatePane;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.bigcat.viewer.util.Maps;
import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

public class SourceTabs implements Supplier< Node >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

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

	private final HashMap< Source< ? >, StatePane > statePaneCache = new HashMap<>();

	private final ObservableList< StatePane > statePanes = FXCollections.observableArrayList();
	{
		InvokeOnJavaFXApplicationThread.invoke( () -> statePanes.addListener( ( ListChangeListener< StatePane > ) change -> this.contents.getChildren().setAll(
				statePanes.stream().map( StatePane::get ).collect( Collectors.toList() ) ) ) );
	}

	private final SourceInfo info;

	private final DoubleProperty width = new SimpleDoubleProperty();

	public SourceTabs(
			final ObservableIntegerValue currentSourceIndex,
			final Consumer< Source< ? > > remove,
			final SourceInfo info )
	{
		this.info = info;
		width.set( 200 );
		this.info.trackSources().addListener( ( ListChangeListener< Source< ? > > ) change -> {
			final ArrayList< Source< ? > > copy = new ArrayList<>( this.info.trackSources() );
			LOG.debug( "Current sources: {}", copy );
			final List< StatePane > show = copy.stream().map( source -> Maps.getOrDefaultFromSupplier( statePaneCache, source, () -> new StatePane(
					info.getState( source ),
					info,
					s -> removeDialog( remove, s ),
					width ) ) ).collect( Collectors.toList() );
			new ArrayList<>( this.statePanes ).forEach( StatePane::unbind );
			show.forEach( StatePane::bind );
			this.statePanes.setAll( show );
		} );

		this.info.removedSourcesTracker().addListener( ( ListChangeListener< Source< ? > > ) change -> {
			final ArrayList< ? extends Source< ? > > list = new ArrayList<>( change.getList() );
			list
					.stream()
					.map( statePaneCache::remove )
					.map( Optional::ofNullable )
					.forEach( o -> o.ifPresent( StatePane::unbind ) );
		} );

	}

	@Override
	public Node get()
	{
		return sp;
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

	public DoubleProperty widthProperty()
	{
		return this.width;
	}

}
