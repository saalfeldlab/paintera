package bdv.bigcat.viewer.atlas.mode;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import bdv.bigcat.viewer.state.SelectedIds;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;

public class SelectionDialogSingleId implements HandleMultipleIds
{

	private final String message;

	public SelectionDialogSingleId( final String message )
	{
		super();
		this.message = message;
	}

	@Override
	public boolean handle( final long[] ids, final SelectedIds selectedIds, final boolean[] isActive )
	{
		final CountDownLatch latch = new CountDownLatch( 1 );
		final boolean[] needsAction = { false };

		Platform.runLater( () -> {
			final CheckBox[] checkBoxes = new CheckBox[ ids.length ];
			final RadioButton[] radioButtons = new RadioButton[ ids.length ];

			final ToggleGroup radioButtonGroup = new ToggleGroup();
			for ( int i = 0; i < ids.length; ++i )
			{
				isActive[ i ] = selectedIds.isActive( ids[ i ] );
				final CheckBox cb = new CheckBox( "" );
				checkBoxes[ i ] = cb;
				cb.setDisable( true );
				cb.setSelected( isActive[ i ] );
				final RadioButton rb = new RadioButton( "" );
				rb.setSelected( false );
				rb.setToggleGroup( radioButtonGroup );
				radioButtons[ i ] = rb;
			}

			final GridPane pane = new GridPane();
			for ( int i = 0; i < ids.length; ++i )
			{
				pane.add( new Label( "" + ids[ i ] ), 0, i );
				pane.add( radioButtons[ i ], 1, i );
				pane.add( checkBoxes[ i ], 2, i );
			}
			final Alert alert = new Alert( Alert.AlertType.CONFIRMATION );
			alert.setTitle( "" );
			alert.setHeaderText( message );
			alert.setGraphic( pane );
			final Optional< ButtonType > result = alert.showAndWait();

			if ( result.isPresent() && result.get().equals( ButtonType.OK ) )
				for ( int i = 0; i < isActive.length; ++i )
				{
					final boolean isSelected = radioButtons[ i ].isSelected();
					if ( isActive[ i ] != isSelected )
						needsAction[ 0 ] = true;
					isActive[ i ] = isSelected;
				}
			latch.countDown();
		} );

		try
		{
			latch.await();
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
			return false;
		}
		return needsAction[ 0 ];

	}
}
