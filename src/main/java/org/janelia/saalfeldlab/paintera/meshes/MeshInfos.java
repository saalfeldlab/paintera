package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.paintera.SourceState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MeshInfos
{

	private final ObservableList< MeshInfo > infos = FXCollections.observableArrayList();

	private final ObservableList< MeshInfo > readOnlyInfos = FXCollections.unmodifiableObservableList( infos );

	public MeshInfos(
			final SourceState< ?, ? > state,
			final SelectedSegments selectedSegments,
			final FragmentSegmentAssignment assignment,
			final MeshManager meshManager,
			final int numScaleLevels )
	{
		super();

		selectedSegments.addListener( obs -> {
			final long[] segments = selectedSegments.getSelectedSegments();
			final List< MeshInfo > infos = Arrays
					.stream( segments )
					.mapToObj( id -> new MeshInfo( state, id, assignment, meshManager, numScaleLevels ) )
					.collect( Collectors.toList() );

			this.infos.setAll( infos );
		} );
	}

	public ObservableList< MeshInfo > readOnlyInfos()
	{
		return this.readOnlyInfos;
	}

}
