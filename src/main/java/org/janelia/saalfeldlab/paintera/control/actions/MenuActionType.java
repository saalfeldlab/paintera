package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;
import java.util.Set;

public enum MenuActionType implements ActionType {
	AddSource,
	ChangeActiveSource,
	ToggleSidePanel,
	ResizePanel,
	ToggleToolBarMode,
	ToggleToolBarVisibility,
	ToggleMenuBarVisibility,
	ToggleMenuBarMode,
	ToggleStatusBarVisibility,
	ToggleStatusBarMode,
	ToggleMaximizeViewer,
	ResizeViewers,
	OrthoslicesContextMenu,
	SaveProject,
	CommitCanvas,
	CreateLabelSource,
	CreateVirtualSource,
	LoadProject,
	DetachViewer,

	OpenProject,
	ExportSource;

	public static EnumSet<MenuActionType> of(final MenuActionType first, final MenuActionType... rest) {

		return EnumSet.of(first, rest);
	}

	public static Set<? extends ActionType> all() {

		return EnumSet.allOf(MenuActionType.class);
	}

	public static EnumSet<MenuActionType> none() {

		return EnumSet.noneOf(MenuActionType.class);
	}
}
