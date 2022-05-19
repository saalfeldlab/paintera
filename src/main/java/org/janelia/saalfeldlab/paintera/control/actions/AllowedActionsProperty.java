package org.janelia.saalfeldlab.paintera.control.actions;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Cursor;

import java.util.Optional;

/**
 * This class is an {@link ObservableValue} over {@link AllowedActions}.
 * <p>
 * This also provides abstract calls for:
 * <li>{@link AllowedActionsProperty#enable() enabling} / {@link AllowedActionsProperty#disable() disabling} current actions</li>
 * <li>{@link AllowedActionsProperty#isAllowed(ActionType) checking if an action is allowed}</li>
 * <li>{@link AllowedActionsProperty#hasPermission(ActionType) checking if an action is permitted}</li>
 */
public class AllowedActionsProperty extends SimpleObjectProperty<AllowedActions> {

  private static final AllowedActions EMPTY_ACTION_SET = new AllowedActions.AllowedActionsBuilder().create();

  private final ObjectProperty<Cursor> cursorProperty;
  private final ChangeListener<Cursor> cursorChangeListener;
  private final BooleanProperty isDisabled = new SimpleBooleanProperty(false);
  private Cursor previousCursor = Cursor.DEFAULT;
  private AllowedActions disabledActions;
  private boolean currentlyProcessingEnableDisable = false;

  public AllowedActionsProperty(final ObjectProperty<Cursor> cursorProperty) {

	this(null, "", cursorProperty);
  }

  public AllowedActionsProperty(final AllowedActions initialValue, final ObjectProperty<Cursor> cursorProperty) {

	this(null, "", initialValue, cursorProperty);
  }

  public AllowedActionsProperty(final Object bean, final String name, final ObjectProperty<Cursor> cursorProperty) {

	this(bean, name, null, cursorProperty);
  }

  public AllowedActionsProperty(final Object bean, final String name, final AllowedActions initialValue, final ObjectProperty<Cursor> cursorProperty) {

	super(bean, name, initialValue);
	this.cursorProperty = cursorProperty;
	this.cursorChangeListener = createCursorChangeListener(this.cursorProperty);
	this.isDisabled.addListener(this::disableActionsListener);
  }

  private static ChangeListener<Cursor> createCursorChangeListener(final ObjectProperty<Cursor> cursorProp) {

	return (observable, oldValue, newValue) -> {
	  if (newValue != Cursor.WAIT) {
		cursorProp.set(Cursor.WAIT);
	  }
	};
  }

  /**
   * Ensures all calls to {@link #isAllowed(ActionType action)} returns {@code false} regardless of whether an {@code action} {@link AllowedActionsProperty#hasPermission(ActionType action)}.
   */
  public void disable() {

	currentlyProcessingEnableDisable = true;
	isDisabled.set(true);
	currentlyProcessingEnableDisable = false;
  }

  /**
   * Allows calls to {@link #isAllowed(ActionType action)} to return value of {@link AllowedActionsProperty#hasPermission(ActionType action)}.
   */
  public void enable() {

	currentlyProcessingEnableDisable = true;
	isDisabled.set(false);
	currentlyProcessingEnableDisable = false;
  }

  private void disableActionsListener(final ObservableValue<? extends Boolean> obs, final Boolean previouslyDisabled, final Boolean disable) {
	/* Do nothing if no change */
	if (previouslyDisabled == disable)
	  return;
	if (disable) {
	  /* store the current cursor*/
	  this.previousCursor = this.cursorProperty.get();
	  cursorProperty.addListener(cursorChangeListener);
	  this.cursorProperty.set(Cursor.WAIT);
	} else {
	  this.cursorProperty.removeListener(cursorChangeListener);
	  this.cursorProperty.set(this.previousCursor);
	}
  }

  /**
   * Check to see if the allowed actions are {@link AllowedActionsProperty#isDisabled disabled}, and if not, whether {@code action} is allowed with current permissions.
   *
   * @param action to check permissions and disability status for.
   * @return true if this action is allowed, and allowedActions is not {@link AllowedActionsProperty#isDisabled disabled}.
   */
  public boolean isAllowed(ActionType action) {

	if (isDisabled.get()) {
	  return false;
	} else {
	  return Optional.ofNullable(get()).map(it -> it.isAllowed(action)).orElse(false);
	}
  }

  /**
   * Checks to see if {@code action} is present in current permissions.
   * <p>Note: This differs from {@link #isAllowed(ActionType)} because it does not depend on {@link AllowedActionsProperty#isDisabled}. </p>
   *
   * @param action to check permission for.
   * @return true if the permission for this action is present.
   */
  public boolean hasPermission(ActionType action) {

	return Optional.ofNullable(get()).map(it -> it.isAllowed(action)).orElse(false);
  }

  /**
   * @param action to provide a boolean binding for.
   * @return a BooleanBinding which reflects whether {@code action} is currently {@link AllowedActionsProperty#isAllowed(ActionType) allowed}.
   */
  public BooleanBinding allowedActionBinding(ActionType action) {

	return Bindings.createBooleanBinding(() -> isAllowed(action), this);
  }

  /**
   * @param action to provide a boolean binding for
   * @return a BooleanBinding which reflects whether {@code action} currently {@link AllowedActionsProperty#hasPermission has valid permission}.
   */
  public BooleanBinding hasPermissionBinding(ActionType action) {

	return Bindings.createBooleanBinding(() -> hasPermission(action), this);
  }
}
