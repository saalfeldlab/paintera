package org.janelia.saalfeldlab.paintera.control.actions;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Cursor;
import javafx.scene.Node;

public class AllowedActionsProperty extends SimpleObjectProperty<AllowedActions> {

  private static final AllowedActions EMPTY_ACTION_SET = new AllowedActions.AllowedActionsBuilder().create();

  private final Node ownerNode;
  private final ChangeListener<Cursor> cursorChangeListener;
  private final BooleanProperty isDisabled = new SimpleBooleanProperty(false);
  private Cursor previousCursor = Cursor.DEFAULT;
  private AllowedActions disabledActions;
  private boolean currentlyProcessingEnableDisable = false;

  public AllowedActionsProperty(final Node ownerNode) {

	this(null, "", ownerNode);
  }

  public AllowedActionsProperty(final AllowedActions initialValue, final Node ownerNode) {

	this(null, "", initialValue, ownerNode);
  }

  public AllowedActionsProperty(final Object bean, final String name, final Node ownerNode) {

	this(bean, name, null, ownerNode);
  }

  public AllowedActionsProperty(final Object bean, final String name, final AllowedActions initialValue, final Node ownerNode) {

	super(bean, name, initialValue);
	this.ownerNode = ownerNode;
	this.cursorChangeListener = createCursorChangeListener(this.ownerNode);
	this.isDisabled.addListener(this::disableActionsListener);
  }

  private static ChangeListener<Cursor> createCursorChangeListener(final Node scene) {

	return (observable, oldValue, newValue) -> {
	  if (newValue != Cursor.WAIT) {
		scene.setCursor(Cursor.WAIT);
	  }
	};
  }

  public void disable() {

	currentlyProcessingEnableDisable = true;
	isDisabled.set(true);
	currentlyProcessingEnableDisable = false;
  }

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
	  disabledActions = getValue();
	  set(EMPTY_ACTION_SET);
	  /* store the current cursor*/
	  this.previousCursor = this.ownerNode.getCursor();
	  this.ownerNode.cursorProperty().addListener(cursorChangeListener);
	  this.ownerNode.setCursor(Cursor.WAIT);
	} else {
	  this.ownerNode.cursorProperty().removeListener(cursorChangeListener);
	  this.ownerNode.setCursor(this.previousCursor);
	  set(disabledActions);
	  disabledActions = null;
	}
  }

  public boolean isProcessingEnableDisable() {

	return currentlyProcessingEnableDisable;
  }

  public boolean isAllowed(ActionType action) {

	return get().isAllowed(action);
  }
}
