package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum PaintActionType implements ActionType {
	Paint,
	Erase,
	Background,
	Fill,
	Intersect,
	SetBrushSize,
	SetBrushDepth,
	ShapeInterpolation,
	SegmentAnything,
	Smooth,
	Dilate,
	Erode;

	public static EnumSet<PaintActionType> of(final PaintActionType first, final PaintActionType... rest) {

		return EnumSet.of(first, rest);
	}

	public static EnumSet<PaintActionType> all() {

		return EnumSet.allOf(PaintActionType.class);
	}

	public static EnumSet<PaintActionType> none() {

		return EnumSet.noneOf(PaintActionType.class);
	}
}
