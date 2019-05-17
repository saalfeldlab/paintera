package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

/**
 * Describes what actions in the UI are allowed in the current application mode.
 */
public final class AllowedActions
{
	private final EnumSet<NavigationAction> navigationAllowedActions;
	private final EnumSet<SelectIdAction> selectIdAllowedActions;
	private final EnumSet<PaintAction> paintAllowedActions;

	public AllowedActions(
			final EnumSet<NavigationAction> navigationAllowedActions,
			final EnumSet<SelectIdAction> selectIdAllowedActions,
			final EnumSet<PaintAction> paintAllowedActions)
	{
		this.navigationAllowedActions = navigationAllowedActions;
		this.selectIdAllowedActions = selectIdAllowedActions;
		this.paintAllowedActions = paintAllowedActions;
	}

	public boolean isAllowed(final NavigationAction navigationAction)
	{
		return this.navigationAllowedActions.contains(navigationAction);
	}

	public boolean isAllowed(final SelectIdAction selectIdAction)
	{
		return this.selectIdAllowedActions.contains(selectIdAction);
	}

	public boolean isAllowed(final PaintAction paintAction)
	{
		return this.paintAllowedActions.contains(paintAction);
	}

	public static AllowedActions all()
	{
		return new AllowedActions(
			NavigationAction.all(),
			SelectIdAction.all(),
			PaintAction.all()
		);
	}
}
