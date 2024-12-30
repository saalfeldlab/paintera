package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum LabelActionType implements ActionType {
	View(false),
	Toggle(false),
	Append(false ),
	CreateNew,
	Lock(false),
	Merge,
	Split,
	SelectAll(false),
	Replace,
	Delete;

	//TODO Caleb: consider moving this to ActionType. Maybe others too
	private final boolean writeRequired;

	LabelActionType() {
		this(true);
	}

	LabelActionType(boolean requiresWrite) {
		this.writeRequired = requiresWrite;
	}

	public boolean isWriteRequired() {

		return writeRequired;
	}

	public static EnumSet<LabelActionType> of(final LabelActionType first, final LabelActionType... rest) {

		return EnumSet.of(first, rest);
	}

	public static EnumSet<LabelActionType> all() {

		return EnumSet.allOf(LabelActionType.class);
	}

	public static EnumSet<LabelActionType> readOnly() {

		var readOnly = EnumSet.noneOf(LabelActionType.class);
		all().stream()
				.filter(action -> !action.writeRequired)
				.forEach(readOnly::add);
		return readOnly;
	}

	public static EnumSet<LabelActionType> none() {

		return EnumSet.noneOf(LabelActionType.class);
	}
}
