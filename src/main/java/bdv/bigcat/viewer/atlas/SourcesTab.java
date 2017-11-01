package bdv.bigcat.viewer.atlas;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import bdv.bigcat.viewer.atlas.Specs.SourceState;
import bdv.bigcat.viewer.atlas.data.DatasetSpec;
import bdv.bigcat.viewer.state.StateListener;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

public class SourcesTab extends Pane implements StateListener< Specs >, ListChangeListener< SourceState< ?, ?, ? > >
{

	private final Specs specs;

	private final ObservableList< Specs.SourceState< ?, ?, ? > > sourceStates = FXCollections.observableArrayList();

	private final IntegerProperty selectedSourceIndex;

//	private static final Image EYE = new Image( "https://openclipart.org/image/300px/svg_to_png/182888/1378233381.png" );

	private final ListView< Specs.SourceState< ?, ?, ? > > sourcesList = new ListView<>( sourceStates );
	{
		sourcesList.setCellFactory( param -> new Cell() );
		this.getChildren().add( sourcesList );
		sourcesList.prefWidthProperty().bind( this.prefWidthProperty() );
		sourcesList.prefHeightProperty().bind( this.prefHeightProperty() );
	}

	private class Cell extends ListCell< Specs.SourceState< ?, ?, ? > >
	{

		private final HBox contents = new HBox();

		Label visibilityLabel = new Label();

		CheckBox toggleVisibilityCheckBox = new CheckBox();

//		ImageView eye = new ImageView( EYE );
		{
			contents.getChildren().add( visibilityLabel );
//			eye.setPreserveRatio( true );
		}

		@Override
		protected void updateItem( final Specs.SourceState< ?, ?, ? > item, final boolean empty )
		{
			super.updateItem( item, empty );
			if ( empty || item == null )
				Platform.runLater( () -> setText( null ) );
			else
			{
				final DatasetSpec< ?, ? > spec = item.spec();
				Platform.runLater( () -> setText( spec.name() ) );
				toggleVisibilityCheckBox.selectedProperty().set( item.isVisible() );
				Platform.runLater( () -> visibilityLabel.setGraphic( contents ) );
				setGraphic( toggleVisibilityCheckBox );
				toggleVisibilityCheckBox.setOnMouseClicked( click -> specs.setVisibility( spec, !item.isVisible() ) );
//				toggleVisibilityCheckBox.addEventHandler( MouseEvent.MOUSE_CLICKED, click -> {
//					final boolean isVisible = item.isVisible();
//					specs.setVisibility( spec, !isVisible );
//				} );
//				toggleVisibilityCheckBox.selectedProperty().addListener( ( observable, oldv, newv ) -> {
//					specs.setVisibility( item.spec(), !newv );
//				} );
				item.visibleProperty().addListener( ( observable, oldv, newv ) -> {
					toggleVisibilityCheckBox.selectedProperty().set( item.isVisible() );
				} );
			}
		}
	}

	public SourcesTab( final Specs specs )
	{
		synchronized ( specs )
		{
			this.specs = specs;
			update();
			final Optional< DatasetSpec< ?, ? > > selectedSource = specs.selectedSourceProperty().getValue();
			this.selectedSourceIndex = new SimpleIntegerProperty( selectedSource.isPresent() ? Specs.indexOf( sourceStates, selectedSource.get() ) : -1 );
			this.specs.addListChangeListener( this );
			this.specs.addListener( this );
			this.specs.selectedSourceProperty().addListener( ( ChangeListener< Optional< DatasetSpec< ?, ? > > > ) ( observable, oldValue, newValue ) -> {
				this.selectedSourceIndex.set( newValue.isPresent() ? Specs.indexOf( sourceStates, newValue.get() ) : -1 );
				sourcesList.selectionModelProperty().get().select( this.selectedSourceIndex.get() );
			} );
		}
	}

	private synchronized void update()
	{
		synchronized ( specs )
		{
			final List< SourceState< ?, ?, ? > > states = specs.sourceStates();
			if ( sourcesChanged( states ) )
				Platform.runLater( () -> {
					sourceStates.clear();
					sourceStates.addAll( states );
				} );
		}
	}

	private synchronized boolean sourcesChanged( final List< SourceState< ?, ?, ? > > states )
	{
		return states.size() != sourceStates.size() || IntStream.range( 0, states.size() ).filter( i -> states.get( i ) != sourceStates.get( i ) ).count() > 0;
	}

	@Override
	public void stateChanged()
	{
		update();
	}

	@Override
	public void onChanged( final Change< ? extends SourceState< ?, ?, ? > > c )
	{
		while ( c.next() )
			if ( c.wasAdded() )
				update();
			else if ( c.wasRemoved() )
				update();
			else if ( c.wasPermutated() )
				update();
			else if ( c.wasReplaced() )
				update();
	}

	public static List< SourceState< ?, ?, ? > > getVisibleOnly( final Collection< SourceState< ?, ?, ? > > states )
	{
		return states.stream().filter( SourceState::isVisible ).collect( Collectors.toList() );
	}

}
