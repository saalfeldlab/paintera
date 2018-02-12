package bdv.bigcat.viewer.meshes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import bdv.bigcat.viewer.state.FragmentSegmentAssignment;
import bdv.bigcat.viewer.state.SelectedSegments;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MeshInfos
{

	private final ObservableList< MeshInfo > infos = FXCollections.observableArrayList();

	private final ObservableList< MeshInfo > readOnlyInfos = FXCollections.unmodifiableObservableList( infos );

	public MeshInfos(
			final SelectedSegments< ? > selectedSegments,
			final FragmentSegmentAssignment assignment,
			final MeshManager meshManager,
			final int numScaleLevels )
	{
		super();

		selectedSegments.addListener( () -> {
			final long[] segments = selectedSegments.getSelectedSegments();
			final List< MeshInfo > infos = Arrays
					.stream( segments )
					.mapToObj( id -> new MeshInfo( id, assignment, meshManager, numScaleLevels ) )
					.collect( Collectors.toList() );

			this.infos.setAll( infos );
		} );
	}

	public ObservableList< MeshInfo > readOnlyInfos()
	{
		return this.readOnlyInfos;
	}

}
