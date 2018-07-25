package org.janelia.saalfeldlab.paintera.control.assignment;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FragmentSegmentAssignmentStateWithActionTracker extends ObservableWithListenersList
		implements FragmentSegmentAssignmentState
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected List<AssignmentAction> actions = new ArrayList<>();

	public void persist() throws UnableToPersist
	{
		throw new UnableToPersist(new UnsupportedOperationException("Not implemented yet!"));
	}

	protected abstract void applyImpl(final AssignmentAction action);

	@Override
	public void apply(final AssignmentAction action)
	{
		applyImpl(action);
		this.actions.add(action);
		stateChanged();
	}

	@Override
	public void apply(final Collection<? extends AssignmentAction> actions)
	{
		actions.forEach(this::applyImpl);
		this.actions.addAll(actions);
		stateChanged();
	}

	public List<AssignmentAction> getActionsCopy()
	{
		return Collections.unmodifiableList(this.actions);
	}

}
