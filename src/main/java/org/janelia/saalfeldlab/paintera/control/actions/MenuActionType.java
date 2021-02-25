package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum MenuActionType implements ActionType {
  AddSource,
  ChangeActiveSource,
  SidePanel,
  ToggleMaximizeViewer,
  OrthoslicesContextMenu,
  SaveProject,
  CommitCanvas;

  public static EnumSet<MenuActionType> of(final MenuActionType first, final MenuActionType... rest) {

	return EnumSet.of(first, rest);
  }

  public static EnumSet<MenuActionType> all() {

	return EnumSet.allOf(MenuActionType.class);
  }

  public static EnumSet<MenuActionType> none() {

	return EnumSet.noneOf(MenuActionType.class);
  }
}
