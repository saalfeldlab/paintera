package bdv.bigcat.util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class Selection<T> extends HashSet<T> {

	private static final long serialVersionUID = 1L;
	
	private final List<SelectionListener<T>> listeners = new LinkedList<SelectionListener<T>>();
	private T lastAdded = null;
	
	public interface SelectionListener<T> {
		
		public void itemSelected(T t);

		public void itemUnselected(T t);
		
		public void selectionCleared();
	}

	@Override
	public boolean add(T t) {
		
		boolean added = super.add(t);
		lastAdded = t;
		notifyItemAdded(t);
		return added;
	}

	@Override
	public boolean remove(Object t) {
		
		boolean removed = super.remove(t);
		notifyItemRemoved((T)t);
		return removed;
	}
	
	@Override
	public void clear() {
		
		super.clear();
		lastAdded = null;
		notifySelectionCleared();
	}
	
	public T getLastAdded() {
		
		return lastAdded;
	}
	
	public void addSelectionListener(SelectionListener<T> listener) {
		
		listeners.add(listener);
	}
	
	public void removeSelectionListener(SelectionListener<T> listener) {
		
		listeners.remove(listener);
	}
	
	protected void notifyItemAdded(T t) {
		
		for (SelectionListener<T> l : listeners)
			l.itemSelected(t);
	}
	
	protected void notifyItemRemoved(T t) {
		
		for (SelectionListener<T> l : listeners)
			l.itemUnselected(t);
	}
	
	protected void notifySelectionCleared() {
		
		for (SelectionListener<T> l : listeners)
			l.selectionCleared();
	}
}
