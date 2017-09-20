package bdv.bigcat.viewer.state;

import java.util.ArrayList;

public class AbstractState< T extends AbstractState< T > >
{
	private final ArrayList< StateListener< T > > listeners = new ArrayList<>();

	public synchronized void addListener( final StateListener< T > listener )
	{
		this.listeners.add( listener );
	}

	public synchronized boolean removeListener( final StateListener< T > listener )
	{
		return this.listeners.remove( listener );
	}

	protected void stateChanged()
	{
		listeners.forEach( StateListener::stateChanged );
	}

}
