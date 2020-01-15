package org.janelia.saalfeldlab.paintera.state;

import javafx.beans.property.ObjectProperty;

@Deprecated
public interface HasFloodFillState {

	public static class FloodFillState {

		public final long labelId;
		public final Runnable interrupt;

		public FloodFillState(final long labelId, final Runnable interrupt)
		{
			this.labelId = labelId;
			this.interrupt = interrupt;
		}
	}

	ObjectProperty<FloodFillState> floodFillState();
}
