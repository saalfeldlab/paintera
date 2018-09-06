package org.janelia.saalfeldlab.paintera.control.assignment;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Pair;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.undo.HasHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FragmentSegmentAssignmentStateWithActionTracker extends ObservableWithListenersList
		implements FragmentSegmentAssignmentState, HasHistory<Pair<AssignmentAction, BooleanProperty>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected ObservableList<Pair<AssignmentAction, BooleanProperty>> actions = FXCollections.observableArrayList();

	private ObservableList<Pair<AssignmentAction, BooleanProperty>> readOnlyActions = FXCollections.unmodifiableObservableList(actions);

	public void persist() throws UnableToPersist
	{
		throw new UnableToPersist(new UnsupportedOperationException("Not implemented yet!"));
	}

	protected abstract void applyImpl(final AssignmentAction action);

	private void removeDisabledActions()
	{
		List<Pair<AssignmentAction, BooleanProperty>> onlyEnabledActions = actions
				.stream()
				.filter(p -> p.getValue().get()).collect(Collectors.toList());

		if (onlyEnabledActions.size() != actions.size())
		{
			actions.clear();
			actions.addAll(onlyEnabledActions);
		}
	}

	private void applyNoStateChange(final AssignmentAction action)
	{
		removeDisabledActions();
		applyImpl(action);
		Pair<AssignmentAction, BooleanProperty> toggleableAction = new Pair<>(
				action,
				new SimpleBooleanProperty(true)
		);
		toggleableAction.getValue().addListener(obs -> reapplyActionsAndNoitfy());
		this.actions.add(toggleableAction);
	}

	@Override
	public void apply(final AssignmentAction action)
	{
		removeDisabledActions();
		applyNoStateChange(action);
		stateChanged();
	}

	@Override
	public void apply(final Collection<? extends AssignmentAction> actions)
	{
		removeDisabledActions();
		actions.forEach(this::applyNoStateChange);
		stateChanged();
	}

	public ObservableList<Pair<AssignmentAction, BooleanProperty>> events()
	{
		return readOnlyActions;
	}

	private void reapplyActionsAndNoitfy()
	{
		reapplyActions();
		stateChanged();
	}

	protected abstract void reapplyActions();

}
