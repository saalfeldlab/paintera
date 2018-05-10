package org.janelia.saalfeldlab.paintera.serialization;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;

import com.google.gson.GsonBuilder;

import net.imglib2.realtransform.AffineTransform3D;

public class GsonHelpers
{

	public static GsonBuilder builderWithAllRequiredAdapters()
	{
		final SourceStateSerializer sss = new SourceStateSerializer();

		return new GsonBuilder()
				.registerTypeHierarchyAdapter( SourceInfo.class, new SourceInfoSerializer() )
				.registerTypeHierarchyAdapter( SourceStateWithIndexedDependencies.class, sss )
				.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() )
				.registerTypeHierarchyAdapter( Composite.class, new CompositeSerializer() )
				.registerTypeAdapter( SelectedIds.class, new SelectedIdsSerializer() )
				.registerTypeAdapter( FragmentSegmentAssignmentOnlyLocal.class, new FragmentSegmentAssignmentOnlyLocalSerializer() )
				.registerTypeAdapter( WindowProperties.class, new WindowPropertiesSerializer() );
	}

}
