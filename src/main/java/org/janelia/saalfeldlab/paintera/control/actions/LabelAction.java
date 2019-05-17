package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum LabelAction
{
	Toggle,
	Append,
	CreateNew,
	Lock,
	Merge,
	Split;

	public static EnumSet<LabelAction> of(final LabelAction first, final LabelAction... rest)
	{
		return EnumSet.of(first, rest);
	}

	public static EnumSet<LabelAction> all()
	{
		return EnumSet.allOf(LabelAction.class);
	}

	public static EnumSet<LabelAction> none()
	{
		return EnumSet.noneOf(LabelAction.class);
	}
}
