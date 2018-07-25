package org.janelia.saalfeldlab.paintera.control.assignment.action;

public interface AssignmentAction
{

	public Type getType();

	public enum Type
	{
		DETACH(Detach.class),
		MERGE(Merge.class);

		private final Class<? extends AssignmentAction> clazz;

		private Type(final Class<? extends AssignmentAction> clazz)
		{
			this.clazz = clazz;
		}

		public Class<? extends AssignmentAction> getClassForType()
		{
			return this.clazz;
		}
	}

}
