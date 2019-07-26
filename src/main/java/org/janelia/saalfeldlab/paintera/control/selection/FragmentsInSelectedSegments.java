package org.janelia.saalfeldlab.paintera.control.selection;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;

import gnu.trove.set.hash.TLongHashSet;

public class FragmentsInSelectedSegments extends ObservableWithListenersList
{

	private final SelectedSegments activeSegments;

	private final TLongHashSet selectedFragments = new TLongHashSet();

	public FragmentsInSelectedSegments(final SelectedSegments activeSegments)
	{
		super();
		this.activeSegments = activeSegments;
		this.activeSegments.addListener(a -> update());
		this.activeSegments.getAssignment().addListener(a -> update());
	}

	public long[] getFragments()
	{
		synchronized (this.selectedFragments)
		{
			return this.selectedFragments.toArray();
		}
	}

	private void update()
	{
		System.out.println("updating");
		synchronized (this.selectedFragments)
		{
			selectedFragments.clear();
			synchronized (activeSegments.getSet()) {
				activeSegments.getSet().forEach(id -> {
					selectedFragments.addAll(activeSegments.getAssignment().getFragments(id));
					return true;
				});
			}
		}
		stateChanged();
	}

	public boolean contains(final long id)
	{
		return this.selectedFragments.contains(id);
	}

}
