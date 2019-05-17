package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum NavigationAction
{
	Drag,
	Scroll,
	Zoom,
	Rotate;

	public static EnumSet<NavigationAction> of(final NavigationAction first, final NavigationAction... rest)
	{
		return EnumSet.of(first, rest);
	}

	public static EnumSet<NavigationAction> all()
	{
		return EnumSet.allOf(NavigationAction.class);
	}

	public static EnumSet<NavigationAction> none()
	{
		return EnumSet.noneOf(NavigationAction.class);
	}
}
