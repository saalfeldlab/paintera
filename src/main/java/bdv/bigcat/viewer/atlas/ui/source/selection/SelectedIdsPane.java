package bdv.bigcat.viewer.atlas.ui.source.selection;

import java.util.Arrays;

import bdv.bigcat.viewer.atlas.ui.BindUnbindAndNodeSupplier;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.state.StateListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;

public class SelectedIdsPane implements BindUnbindAndNodeSupplier
{

	private final SelectedIds selectedIds;

	private final ObservableList< Long > selection = FXCollections.observableArrayList();

	private final SelectionListener selectionListener = new SelectionListener();

	public SelectedIdsPane( final SelectedIds selectedIds )
	{
		super();
		this.selectedIds = selectedIds;
	}

	@Override
	public Node get()
	{
		return new SelectedIdsTextField( selection ).textField();
	}

	@Override
	public void bind()
	{
		selectedIds.addListener( selectionListener );
	}

	@Override
	public void unbind()
	{
		selectedIds.removeListener( selectionListener );
	}

	private class SelectionListener implements StateListener< SelectedIds >, ListChangeListener< Long >
	{

		@Override
		public void stateChanged()
		{
			selection.setAll( Arrays.stream( selectedIds.getActiveIds() ).mapToObj( id -> id ).toArray( Long[]::new ) );
		}

		@Override
		public void onChanged( final Change< ? extends Long > c )
		{
			selectedIds.activate( selection.stream().mapToLong( id -> id ).toArray() );
		}

	}

}
