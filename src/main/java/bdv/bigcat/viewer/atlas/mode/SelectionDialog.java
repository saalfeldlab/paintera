package bdv.bigcat.viewer.atlas.mode;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import bdv.bigcat.viewer.state.SelectedIds;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.FlowPane;

public class SelectionDialog implements HandleMultipleIds
{

	private final String message;

	public SelectionDialog( final String message )
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
			for ( int i = 0; i < ids.length; ++i )
			{
				isActive[ i ] = selectedIds.isActive( ids[ i ] );
				final CheckBox cb = new CheckBox( "" + ids[ i ] );
				cb.setSelected( isActive[ i ] );
				checkBoxes[ i ] = cb;
			}

			final FlowPane pane = new FlowPane( checkBoxes );
			final Alert alert = new Alert( Alert.AlertType.CONFIRMATION );
			alert.setTitle( "" );
			alert.setHeaderText( message );
			alert.setGraphic( pane );

			final Optional< ButtonType > result = alert.showAndWait();

			if ( result.isPresent() && result.get().equals( ButtonType.OK ) )
			{
				needsAction[ 0 ] = true;
				for ( int i = 0; i < isActive.length; ++i )
					isActive[ i ] = checkBoxes[ i ].isSelected();
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
