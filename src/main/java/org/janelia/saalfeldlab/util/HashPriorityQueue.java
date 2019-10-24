package org.janelia.saalfeldlab.util;

import java.util.*;

/**
 * @author Igor Pisarev
 *
 * Provides similar functionality to {@link PriorityQueue>} with addition of fast update and remove operations.
 * All supported operations (add, update, remove, poll) are implemented with O(log N) complexity.
 *
 * @param <P> element priority type
 * @param <E> element type
 */
public class HashPriorityQueue<P, E>
{
	private final TreeMap<P, HashSet<E>> priorityToElements;
	private final HashMap<E, P> elementToPriority;

	/**
	 * Creates the priority queue using the given priority comparator.
	 *
	 * @param comparator
	 */
	public HashPriorityQueue(final Comparator<? super P> comparator)
	{
		priorityToElements = new TreeMap<>(comparator);
		elementToPriority = new HashMap<>();
	}

	/**
	 * Creates a copy of another {@link HashPriorityQueue}.
	 *
	 * @param other
	 */
	public HashPriorityQueue(final HashPriorityQueue<P, E> other)
	{
		priorityToElements = new TreeMap<>(other.priorityToElements.comparator());
		elementToPriority = new HashMap<>(other.elementToPriority);
		for (final Map.Entry<P, HashSet<E>> entry : other.priorityToElements.entrySet())
			priorityToElements.put(entry.getKey(), new HashSet<>(entry.getValue()));
	}

	/**
	 * Inserts or updates the element with a given priority.
	 *
	 * @param priority
	 * @param element
	 * @return {@code true} if the element previously existed and was updated, {@code false} otherwise
	 */
	public boolean addOrUpdate(final P priority, final E element)
	{
		Objects.requireNonNull(priority);
		Objects.requireNonNull(element);

		final boolean wasPresent = remove(element);

		HashSet<E> priorityGroup = priorityToElements.get(priority);
		if (priorityGroup == null)
		{
			priorityGroup = new HashSet<>();
			priorityToElements.put(priority, priorityGroup);
		}
		priorityGroup.add(element);

		elementToPriority.put(element, priority);
		return wasPresent;
	}

	/**
	 * Removes the element from the queue.
	 *
	 * @param element
	 * @return {@code true} if the element existed and was removed, {@code false} otherwise
	 */
	public boolean remove(final E element)
	{
		Objects.requireNonNull(element);

		final P priority = elementToPriority.remove(element);
		if (priority == null)
			return false;

		final HashSet<E> priorityGroup = priorityToElements.get(priority);
		assert priorityGroup != null;
		priorityGroup.remove(element);
		if (priorityGroup.isEmpty())
			priorityToElements.remove(priority);

		return true;
	}

	/**
	 * Tests if an element is contained in the queue.
	 *
	 * @param element
	 * @return {@code true} if the element exists in the queue, {@code false} otherwise
	 */
	public boolean contains(final E element)
	{
		Objects.requireNonNull(element);
		return elementToPriority.containsKey(element);
	}

	/**
	 * Returns the priority of the element in the queue.
	 *
	 * @param element
	 * @return priority
	 */
	public P getPriority(final E element)
	{
		Objects.requireNonNull(element);
		return elementToPriority.get(element);
	}

	/**
	 * Retrieves but does not remove the top priority element in the queue.
	 *
	 * @return top priority element
	 */
	public E peek()
	{
		if (isEmpty())
			return null;

		final HashSet<E> topPriorityGroup = priorityToElements.firstEntry().getValue();
		assert !topPriorityGroup.isEmpty();
		return topPriorityGroup.iterator().next();
	}

	/**
	 * Removes and returns the top priority element of the queue.
	 *
	 * @return top priority element
	 */
	public E poll()
	{
		if (isEmpty())
			return null;

		final HashSet<E> topPriorityGroup = priorityToElements.firstEntry().getValue();
		assert !topPriorityGroup.isEmpty();

		final Iterator<E> it = topPriorityGroup.iterator();
		final E element = it.next();
		it.remove();

		assert elementToPriority.containsKey(element);
		elementToPriority.remove(element);

		if (topPriorityGroup.isEmpty())
			priorityToElements.pollFirstEntry();

		return element;
	}

	/**
	 * Removes and returns the first {#numElements} top priority elements in the queue.
	 * May return fewer elements than requested if the size of the queue is smaller than that.
	 *
	 * @param numElements
	 * @return requested number of elements
	 */
	public List<E> poll(final int numElements)
	{
		if (numElements < 0)
			throw new IllegalArgumentException();

		final List<E> elements = new ArrayList<>();
		for (final Iterator<HashSet<E>> itGroups = priorityToElements.values().iterator(); itGroups.hasNext() && elements.size() < numElements;)
		{
			final HashSet<E> topPriorityGroup = itGroups.next();
			assert !topPriorityGroup.isEmpty();

			for (final Iterator<E> itElements = topPriorityGroup.iterator(); itElements.hasNext() && elements.size() < numElements;)
			{
				final E element = itElements.next();
				itElements.remove();
				elements.add(element);

				assert elementToPriority.containsKey(element);
				elementToPriority.remove(element);
			}

			if (topPriorityGroup.isEmpty())
				itGroups.remove();
		}

		return elements;
	}

	public void clear()
	{
		priorityToElements.clear();
		elementToPriority.clear();
	}

	/**
	 * @return the number of elements in the queue
	 */
	public int size()
	{
		return elementToPriority.size();
	}

	/**
	 * @return {@code true} if the queue is empty
	 */
	public boolean isEmpty()
	{
		assert elementToPriority.isEmpty() == priorityToElements.isEmpty();
		return elementToPriority.isEmpty();
	}
}
