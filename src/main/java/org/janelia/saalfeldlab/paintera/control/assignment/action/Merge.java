package org.janelia.saalfeldlab.paintera.control.assignment.action;

public class Merge implements AssignmentAction
{

	public final long fromFragmentId;

	public final long intoFragmentId;

	public final long segmentId;

	public Merge(final long fromFragmentId, final long intoFragmentId, final long segmentId)
	{
		super();
		this.fromFragmentId = fromFragmentId;
		this.intoFragmentId = intoFragmentId;
		this.segmentId = segmentId;
	}

	@Override
	public String toString()
	{
		return "from=" + fromFragmentId + ", into=" + intoFragmentId + ", segment=" + segmentId;
	}

	@Override
	public Type getType()
	{
		return Type.MERGE;
	}

}
