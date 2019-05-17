package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum PaintAction
{
	Paint,
	Erase,
	Background,
	Fill,
	Restrict,
	SetBrush;

	public static EnumSet<PaintAction> of(final PaintAction first, final PaintAction... rest)
	{
		return EnumSet.of(first, rest);
	}

	public static EnumSet<PaintAction> all()
	{
		return EnumSet.allOf(PaintAction.class);
	}

	public static EnumSet<PaintAction> none()
	{
		return EnumSet.noneOf(PaintAction.class);
	}
}
