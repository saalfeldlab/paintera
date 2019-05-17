package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum SelectIdAction
{
	Toggle,
	Append,
	CreateNewLabel,
	Lock;

	public static EnumSet<SelectIdAction> of(final SelectIdAction first, final SelectIdAction... rest)
	{
		return EnumSet.of(first, rest);
	}

	public static EnumSet<SelectIdAction> all()
	{
		return EnumSet.allOf(SelectIdAction.class);
	}

	public static EnumSet<SelectIdAction> none()
	{
		return EnumSet.noneOf(SelectIdAction.class);
	}
}
