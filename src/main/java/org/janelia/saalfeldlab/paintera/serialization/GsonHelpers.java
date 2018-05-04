package org.janelia.saalfeldlab.paintera.serialization;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.GsonBuilder;

import net.imglib2.realtransform.AffineTransform3D;

public class GsonHelpers
{

	public static GsonBuilder builderWithAllRequiredAdapters()
	{
		final SourceStateSerializer sss = new SourceStateSerializer();

		return new GsonBuilder()
				.registerTypeHierarchyAdapter( SourceInfo.class, new SourceInfoSerializer() )
				.registerTypeHierarchyAdapter( SourceState.class, sss )
				.registerTypeHierarchyAdapter( LabelSourceState.class, sss )
				.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() )
				.registerTypeHierarchyAdapter( Composite.class, new CompositeSerializer() )
				.registerTypeAdapter( SelectedIds.class, new SelectedIdsSerializer() )
				.registerTypeAdapter( FragmentSegmentAssignmentOnlyLocal.class, new FragmentSegmentAssignmentOnlyLocalSerializer() )
				.registerTypeAdapter( WindowProperties.class, new WindowPropertiesSerializer() );
	}

}
