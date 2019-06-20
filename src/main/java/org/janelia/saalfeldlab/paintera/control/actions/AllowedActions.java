package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Describes what actions in the UI are allowed in the current application mode.
 * An optional custom predicate can be specified for any action type to determine if it is allowed (or do extra handling before the action is performed).
 */
public final class AllowedActions
{
	private final Map<ActionType, BooleanSupplier> actions;

	private AllowedActions(final Map<ActionType, BooleanSupplier> actions)
	{
		this.actions = actions;
	}

	public boolean isAllowed(final ActionType actionType)
	{
		return Optional.ofNullable(this.actions.get(actionType)).orElse(() -> false).getAsBoolean();
	}

	public void runIfAllowed(final ActionType actionType, final Runnable action)
	{
		if (isAllowed(actionType))
			action.run();
	}

	/**
	 * Used to create an instance of {@link AllowedActions}.
	 */
	public static class AllowedActionsBuilder
	{
		private static final Map<ActionType, BooleanSupplier> ALL;
		static
		{
			final Set<ActionType> actions = new HashSet<>();
			actions.addAll(NavigationActionType.all());
			actions.addAll(LabelActionType.all());
			actions.addAll(PaintActionType.all());
			actions.addAll(MenuActionType.all());
			ALL = toMap(actions);
		}
		private static Map<ActionType, BooleanSupplier> toMap(final Collection<ActionType> actions)
		{
			return actions.stream().collect(Collectors.toMap(t -> t, t -> () -> true));
		}

		/**
		 * Create a new instance of {@link AllowedActions} with all known actions.
		 *
		 * @return
		 */
		public static AllowedActions all()
		{
			return new AllowedActions(ALL);
		}

		private final Map<ActionType, BooleanSupplier> actions;

		public AllowedActionsBuilder()
		{
			this.actions = new HashMap<>();
		}

		/**
		 * Add one or more allowed {@link ActionType actions}.
		 *
		 * @param first
		 * @param rest
		 * @return
		 */
		public AllowedActionsBuilder add(final ActionType first, final ActionType... rest)
		{
			final Set<ActionType> set = new HashSet<>();
			set.add(first);
			set.addAll(Arrays.asList(rest));
			add(set);
			return this;
		}

		/**
		 * Add a collection of allowed {@link ActionType actions}.
		 *
		 * @param actions
		 * @return
		 */
		public AllowedActionsBuilder add(final Collection<ActionType> actions)
		{
			this.actions.putAll(toMap(actions));
			return this;
		}

		/**
		 * Add an {@link ActionType action} with a custom predicate to determine if it is allowed.
		 *
		 * @param action
		 * @param predicate
		 * @return
		 */
		public AllowedActionsBuilder add(final ActionType action, final BooleanSupplier predicate)
		{
			this.actions.put(action, predicate);
			return this;
		}

		/**
		 * Add a collection of {@link ActionType actions} with a custom predicate to determine if it is allowed.
		 *
		 * @param actions
		 * @return
		 */
		public AllowedActionsBuilder add(final Map<ActionType, BooleanSupplier> actions)
		{
			this.actions.putAll(actions);
			return this;
		}

		/**
		 * Create a new instance of {@link AllowedActions} with the current set of actions.
		 *
		 * @return
		 */
		public AllowedActions create()
		{
			return new AllowedActions(new HashMap<>(this.actions));
		}
	}
}
