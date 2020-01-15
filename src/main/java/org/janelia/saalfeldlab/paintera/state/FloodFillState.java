package org.janelia.saalfeldlab.paintera.state;

public class FloodFillState {

    public final long labelId;
    public final Runnable interrupt;

    public FloodFillState(final long labelId, final Runnable interrupt)
    {
        this.labelId = labelId;
        this.interrupt = interrupt;
    }
}
