package org.janelia.saalfeldlab.paintera.control.selection;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;

import gnu.trove.set.hash.TLongHashSet;

public class FragmentsInSelectedSegments extends ObservableWithListenersList {

  private final SelectedSegments activeSegments;

  private TLongHashSet selectedFragments = new TLongHashSet();

  public FragmentsInSelectedSegments(final SelectedSegments activeSegments) {

	super();
	this.activeSegments = activeSegments;
	this.activeSegments.addListener(a -> update());
	this.activeSegments.getAssignment().addListener(a -> update());
  }

  public long[] getFragments() {

	return this.selectedFragments.toArray();
  }

  public SelectedSegments getSelectedSegments() {

	return this.activeSegments;
  }

  private void update() {

	final TLongHashSet newSelectedFragments = new TLongHashSet();
	synchronized (activeSegments.getSet()) {
	  activeSegments.getSet().forEach(id -> {
		newSelectedFragments.addAll(activeSegments.getAssignment().getFragments(id));
		return true;
	  });
	}
	this.selectedFragments = newSelectedFragments;
	stateChanged();
  }

  public boolean contains(final long id) {

	return this.selectedFragments.contains(id);
  }

}
