package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MeshInfos< T >
{
	private final ObservableList< MeshInfo< T > > infos = FXCollections.observableArrayList();

	private final ObservableList< MeshInfo< T > > readOnlyInfos = FXCollections.unmodifiableObservableList( infos );

	public MeshInfos(
			final SelectedSegments selectedSegments,
			final FragmentSegmentAssignment assignment,
			final MeshManager< T > meshManager,
			final int numScaleLevels )
	{
		super();

		selectedSegments.addListener( obs -> {
			final long[] segments = selectedSegments.getSelectedSegments();
			final List< MeshInfo<T > > infos = Arrays
					.stream( segments )
					.mapToObj( id -> new MeshInfo< >( id, assignment, meshManager, numScaleLevels ) )
					.collect( Collectors.toList() );

			this.infos.setAll( infos );
		} );
	}

	public ObservableList< MeshInfo< T > > readOnlyInfos()
	{
		return this.readOnlyInfos;
	}
}
