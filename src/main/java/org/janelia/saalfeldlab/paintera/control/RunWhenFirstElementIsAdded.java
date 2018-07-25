package org.janelia.saalfeldlab.paintera.control;

import java.util.function.Consumer;

import javafx.collections.ListChangeListener;

public class RunWhenFirstElementIsAdded<T> implements ListChangeListener<T>
{

	final Consumer<Change<? extends T>> onChange;

	public RunWhenFirstElementIsAdded(final Consumer<Change<? extends T>> onChange)
	{
		super();
		this.onChange = onChange;
	}

	@Override
	public void onChanged(final Change<? extends T> change)
	{
		while (change.next())
		{
			if (change.wasAdded() && change.getList().size() == change.getAddedSize())
			{
				onChange.accept(change);
			}
		}
	}


}
