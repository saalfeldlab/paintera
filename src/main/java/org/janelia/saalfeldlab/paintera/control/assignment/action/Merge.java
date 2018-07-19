package org.janelia.saalfeldlab.paintera.control.assignment.action;

public class Merge implements AssignmentAction
{

	public final long fromFragmentId;

	public final long intoFragmentId;

	public final long segmentId;

	public Merge( final long fromFragmentId, final long intoFragmentId, final long segmentId )
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

	@Override
	public int hashCode()
	{
		int hashCode = Long.hashCode( fromFragmentId );
		hashCode = 31 * hashCode + Long.hashCode( intoFragmentId );
		hashCode = 31 * hashCode + Long.hashCode( segmentId );
		return hashCode;
	}

	@Override
	public boolean equals( final Object other )
	{
		return other instanceof Merge ? equalsMerge( ( Merge ) other ) : false;
	}

	private boolean equalsMerge( final Merge that )
	{
		return this.fromFragmentId == that.fromFragmentId
				&& this.intoFragmentId == that.intoFragmentId
				&& this.segmentId == that.segmentId;
	}

}
