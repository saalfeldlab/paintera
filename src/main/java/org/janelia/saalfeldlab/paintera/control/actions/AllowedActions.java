package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

/**
 * Describes what actions in the UI are allowed in the current application mode.
 */
public final class AllowedActions
{
	private final EnumSet<NavigationAction> navigationAllowedActions;
	private final EnumSet<LabelAction> labelAllowedActions;
	private final EnumSet<PaintAction> paintAllowedActions;
	private final EnumSet<MenuAction> menuAllowedActions;

	public AllowedActions(
			final EnumSet<NavigationAction> navigationAllowedActions,
			final EnumSet<LabelAction> labelAllowedActions,
			final EnumSet<PaintAction> paintAllowedActions,
			final EnumSet<MenuAction> menuAllowedActions)
	{
		this.navigationAllowedActions = navigationAllowedActions;
		this.labelAllowedActions = labelAllowedActions;
		this.paintAllowedActions = paintAllowedActions;
		this.menuAllowedActions = menuAllowedActions;
	}

	public boolean isAllowed(final NavigationAction navigationAction)
	{
		return this.navigationAllowedActions.contains(navigationAction);
	}

	public boolean isAllowed(final LabelAction labelAction)
	{
		return this.labelAllowedActions.contains(labelAction);
	}

	public boolean isAllowed(final PaintAction paintAction)
	{
		return this.paintAllowedActions.contains(paintAction);
	}

	public boolean isAllowed(final MenuAction menuAction)
	{
		return this.menuAllowedActions.contains(menuAction);
	}

	public static AllowedActions all()
	{
		return new AllowedActions(
			NavigationAction.all(),
			LabelAction.all(),
			PaintAction.all(),
			MenuAction.all()
		);
	}
}
