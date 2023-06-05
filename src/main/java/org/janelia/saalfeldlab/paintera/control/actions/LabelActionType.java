package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum LabelActionType implements ActionType {
	Toggle,
	Append,
	CreateNew,
	Lock,
	Merge,
	Split,
	SelectAll;

	public static EnumSet<LabelActionType> of(final LabelActionType first, final LabelActionType... rest) {

		return EnumSet.of(first, rest);
	}

	public static EnumSet<LabelActionType> all() {

		return EnumSet.allOf(LabelActionType.class);
	}

	public static EnumSet<LabelActionType> none() {

		return EnumSet.noneOf(LabelActionType.class);
	}
}
