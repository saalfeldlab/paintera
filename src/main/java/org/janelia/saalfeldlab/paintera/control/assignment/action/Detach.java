package org.janelia.saalfeldlab.paintera.control.assignment.action;

public class Detach implements AssignmentAction
{

	public final long fragmentId;

	public final long fragmentFrom;

	public Detach(final long fragmentId, final long fragmentFrom)
	{
		super();
		this.fragmentId = fragmentId;
		this.fragmentFrom = fragmentFrom;
	}

	@Override
	public String toString()
	{
		return "id=" + fragmentId + ", from=" + fragmentFrom;
	}

	@Override
	public Type getType()
	{
		return Type.DETACH;
	}

}
