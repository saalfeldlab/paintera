package org.janelia.saalfeldlab.fx;

import java.util.ArrayList;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

public class ObservableWithListenersList implements Observable
{
	private final ArrayList<InvalidationListener> listeners = new ArrayList<>();

	@Override
	public synchronized void addListener(final InvalidationListener listener)
	{
		this.listeners.add(listener);
		listener.invalidated(this);
	}

	@Override
	public synchronized void removeListener(final InvalidationListener listener)
	{
		this.listeners.remove(listener);
	}

	protected void stateChanged()
	{
		for (int i = 0; i < listeners.size(); ++i)
		{
			listeners.get(i).invalidated(this);
		}
	}

}
