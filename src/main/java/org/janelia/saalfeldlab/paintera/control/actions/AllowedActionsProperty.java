package org.janelia.saalfeldlab.paintera.control.actions;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

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

	private final BooleanProperty isDisabled = new SimpleBooleanProperty(false);

	private boolean suspendPermissions = false;

	public AllowedActionsProperty() {

		this(null, "");
	}

	public AllowedActionsProperty(final AllowedActions initialValue) {

		this(null, "", initialValue);
	}

	public AllowedActionsProperty(final Object bean, final String name) {

		this(bean, name, null);
	}

	public AllowedActionsProperty(final Object bean, final String name, final AllowedActions initialValue) {

		super(bean, name, initialValue);
	}

	/**
	 * Ensures all calls to {@link #isAllowed(ActionType action)} returns {@code false} regardless of whether an {@code action} {@link AllowedActionsProperty#hasPermission(ActionType action)}.
	 */
	public void disable() {

		isDisabled.set(true);
	}

	/**
	 * Allows calls to {@link #isAllowed(ActionType action)} to return value of {@link AllowedActionsProperty#hasPermission(ActionType action)}.
	 */
	public void enable() {

		isDisabled.set(false);
	}

	/**
	 * Check to see if the allowed actions are {@link AllowedActionsProperty#isDisabled disabled} or if permissions are {@link AllowedActionsProperty#suspendPermissions suspended}, and if not, whether {@code action} is allowed with current permissions.
	 *
	 * @param action to check permissions and disability status for.
	 * @return true if this action is allowed, and allowedActions is not {@link AllowedActionsProperty#isDisabled disabled} or {@link AllowedActionsProperty#suspendPermissions suspended}.
	 */
	public boolean isAllowed(ActionType action) {

		if (isDisabled.get() || suspendPermissions) {
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

		if (suspendPermissions) {
			return false;
		}
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

	/**
	 * Temporarily reject all permissions allowed checks
	 */
	public void suspendPermisssions() {

		this.suspendPermissions = true;
	}

	/**
	 * Restore {@link AllowedActionsProperty#suspendPermissions suspended} permissions
	 */
	public void restorePermisssions() {

		this.suspendPermissions = false;
	}
}
