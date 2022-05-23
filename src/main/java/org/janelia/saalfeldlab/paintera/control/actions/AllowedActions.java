package org.janelia.saalfeldlab.paintera.control.actions;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Describes what actions in the UI are allowed in the current application mode.
 * An optional custom predicate can be specified for any action type to determine if it is allowed (or do extra handling before the action is performed).
 */
public final class AllowedActions {

  /**
   * {@link AllowedActions} with all known actions.
   */
  @Nonnull
  public static final AllowedActions ALL = new AllowedActionsBuilder()
		  .add(MenuActionType.all())
		  .add(LabelActionType.all())
		  .add(PaintActionType.all())
		  .add(NavigationActionType.all())
		  .create();

  /**
   * {@link AllowedActions} with Application Control.
   */
  @Nonnull
  public static final AllowedActions APP_CONTROL = new AllowedActionsBuilder()
		  .add(MenuActionType.all()).create();

  /**
   * {@link AllowedActions} with Application Control and Navigation actions.
   */
  @Nonnull
  public static final AllowedActions NAVIGATION = new AllowedActionsBuilder()
		  .add(APP_CONTROL)
		  .add(NavigationActionType.all())
		  .create();

  /**
   * {@link AllowedActions} with Application Control, Navigation, Paint, and Label actions.
   */
  @Nonnull
  public static final AllowedActions PAINT = new AllowedActionsBuilder()
		  .add(NAVIGATION)
		  .add(PaintActionType.all())
		  .add(LabelActionType.all())
		  .create();

  /**
   * {@link AllowedActions} with Application Control, Navigation, and Read-Only Label actions.
   */
  @Nonnull
  public static final AllowedActions VIEW_LABELS = new AllowedActionsBuilder()
		  .add(NAVIGATION)
		  .add(LabelActionType.Toggle, LabelActionType.Append, LabelActionType.SelectAll)
		  .create();

  private final Set<ActionType> actions;

  private AllowedActions(final Collection<ActionType> actions) {

	this.actions = Set.copyOf(actions);
  }

  public boolean isAllowed(final ActionType actionType) {

	return this.actions.contains(actionType);
  }

  /**
   * Used to create an instance of {@link AllowedActions}.
   */
  public static class AllowedActionsBuilder {

	private static Map<ActionType, BooleanSupplier> toMap(final Collection<ActionType> actions) {

	  return actions.stream().collect(Collectors.toMap(t -> t, t -> () -> true));
	}

	private final Set<ActionType> actions;

	public AllowedActionsBuilder() {

	  this.actions = new HashSet<>();
	}

	/**
	 * Add one or more allowed {@link ActionType actions}.
	 *
	 * @param first
	 * @param rest
	 * @return
	 */
	public AllowedActionsBuilder add(final ActionType first, final ActionType... rest) {

	  final Set<ActionType> set = new HashSet<>();
	  set.add(first);
	  set.addAll(Arrays.asList(rest));
	  add((Collection<ActionType>)set);
	  return this;
	}

	/**
	 * Add a collection of allowed {@link ActionType actions}.
	 *
	 * @param actions
	 * @return
	 */
	public AllowedActionsBuilder add(final Collection<ActionType> actions) {

	  this.actions.addAll(actions);
	  return this;
	}

	/**
	 * Create a new instance of {@link AllowedActions} with the current set of actions.
	 *
	 * @return
	 */
	public @Nonnull AllowedActions create() {

	  return new AllowedActions(new HashSet<>(this.actions));
	}

	public AllowedActionsBuilder add(Set<? extends ActionType> all) {

	  for (ActionType actionType : all) {
		add(actionType);
	  }
	  return this;
	}

	public AllowedActionsBuilder add(AllowedActions all) {

	  this.actions.addAll(all.actions);
	  return this;
	}
  }
}
