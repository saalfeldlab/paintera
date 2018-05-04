package org.janelia.saalfeldlab.paintera.data.meta.n5;

import java.io.IOException;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.data.meta.LabelMeta;
import org.janelia.saalfeldlab.paintera.data.meta.exception.AssignmentCreationFailed;
import org.janelia.saalfeldlab.paintera.data.meta.exception.CommitCanvasCreationFailed;
import org.janelia.saalfeldlab.paintera.data.meta.exception.IdServiceCreationFailed;
import org.janelia.saalfeldlab.paintera.id.IdService;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.numeric.integer.UnsignedLongType;

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

	public default BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > commitCanvas() throws CommitCanvasCreationFailed
	{
		try
		{
			return new CommitCanvasN5( writer(), dataset() );
		}
		catch ( IOException e )
		{
			throw new CommitCanvasCreationFailed( "Unable to create n5 canvas commiter: " + e.getMessage(), e );
		}
	}
}
