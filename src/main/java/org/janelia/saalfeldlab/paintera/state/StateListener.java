package org.janelia.saalfeldlab.paintera.state;

public interface StateListener< T extends AbstractState< T > >
{

	public void stateChanged();

}
