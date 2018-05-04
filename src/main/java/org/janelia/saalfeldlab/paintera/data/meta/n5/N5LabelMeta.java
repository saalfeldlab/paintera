package org.janelia.saalfeldlab.paintera.data.meta.n5;

import java.io.IOException;

import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.data.meta.LabelMeta;
import org.janelia.saalfeldlab.paintera.data.meta.exception.AssignmentCreationFailed;
import org.janelia.saalfeldlab.paintera.data.meta.exception.IdServiceCreationFailed;
import org.janelia.saalfeldlab.paintera.id.IdService;

public interface N5LabelMeta extends N5Meta, LabelMeta
{

	public default FragmentSegmentAssignmentState assignment() throws AssignmentCreationFailed
	{
		try
		{
			return N5Helpers.assignments( writer(), dataset() );
		}
		catch ( IOException e )
		{
			throw new AssignmentCreationFailed( "Unable to create to generate fragment-segment assignments: " + e.getMessage(), e );
		}
	}

	public default FragmentSegmentAssignmentState assignment( long[] fragments, long[] segments ) throws AssignmentCreationFailed
	{
		try
		{
			return N5Helpers.assignments( writer(), dataset(), fragments, segments );
		}
		catch ( IOException e )
		{
			throw new AssignmentCreationFailed( "Unable to create to generate fragment-segment assignments: " + e.getMessage(), e );
		}
	}

	public default IdService idService() throws IdServiceCreationFailed
	{
		try
		{
			return N5Helpers.idService( writer(), dataset() );
		}
		catch ( IOException e )
		{
			throw new IdServiceCreationFailed( "Unable to create n5 id service: " + e.getMessage(), e );
		}
	}
}
