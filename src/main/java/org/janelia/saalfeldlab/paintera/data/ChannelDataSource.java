package org.janelia.saalfeldlab.paintera.data;

public interface ChannelDataSource<D, T> extends DataSource<D, T> {

	long numChannels();

}
