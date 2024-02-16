package org.janelia.saalfeldlab.paintera.state;

//TODO Caleb: Try and remove this, use the task that flood filling creates to interrogate the status instead.
public class FloodFillState {

	public final long labelId;
	public final Runnable interrupt;

	public FloodFillState(final long labelId, final Runnable interrupt) {

		this.labelId = labelId;
		this.interrupt = interrupt;
	}
}
