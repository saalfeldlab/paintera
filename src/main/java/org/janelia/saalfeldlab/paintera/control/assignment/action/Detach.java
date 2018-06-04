package org.janelia.saalfeldlab.paintera.control.assignment.action;

public class Detach
{

	public final long fragmentId;

	public final long segmentFrom;

	public Detach( final long fragmentId, final long segmentFrom )
	{
		super();
		this.fragmentId = fragmentId;
		this.segmentFrom = segmentFrom;
	}

	@Override
	public String toString()
	{
		return "from=" + fragmentId + ", segment=" + segmentFrom;
	}

}
