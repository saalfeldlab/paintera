package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum MenuAction
{
	AddSource,
	CreateNewLabelSource,
	ChangeActiveSource,
	SidePanel,
	ToggleViewerMaximizedMinimized,
	ToggleViewerAndOrthoslicesView,
	OrthoslicesContextMenu,
	SaveProject,
	CommitCanvas;

	public static EnumSet<MenuAction> of(final MenuAction first, final MenuAction... rest)
	{
		return EnumSet.of(first, rest);
	}

	public static EnumSet<MenuAction> all()
	{
		return EnumSet.allOf(MenuAction.class);
	}

	public static EnumSet<MenuAction> none()
	{
		return EnumSet.noneOf(MenuAction.class);
	}
}
