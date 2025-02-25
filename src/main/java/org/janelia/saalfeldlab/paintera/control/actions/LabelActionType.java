package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum LabelActionType implements ActionType {
	Toggle(true),
	Append(true ),
	CreateNew,
	Lock(true),
	Merge,
	Split,
	SelectAll(true),
	Replace,
	Delete;

	//TODO Caleb: consider moving this to ActionType. Maybe others too
	private final boolean readOnly;

	LabelActionType() {
		this(false);
	}

	LabelActionType(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public boolean isReadOnly() {

		return readOnly;
	}

	public static EnumSet<LabelActionType> of(final LabelActionType first, final LabelActionType... rest) {

		return EnumSet.of(first, rest);
	}

	public static EnumSet<LabelActionType> all() {

		return EnumSet.allOf(LabelActionType.class);
	}

	public static EnumSet<LabelActionType> readOnly() {

		var readOnly = EnumSet.noneOf(LabelActionType.class);
		all().stream().filter(LabelActionType::isReadOnly).forEach(readOnly::add);
		return readOnly;
	}

	public static EnumSet<LabelActionType> none() {

		return EnumSet.noneOf(LabelActionType.class);
	}
}
