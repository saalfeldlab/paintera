package org.janelia.saalfeldlab.util.concurrent;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the order of prioritized task execution.
 */
public class PriorityExecutorServiceTest {

	@Test
	public void test() throws InterruptedException {

		final PriorityExecutorService<Integer> priorityExecutorService = PriorityExecutors.newPriorityFixedThreadPool(1);
		final List<Integer> result = new ArrayList<>();

		final List<Pair<Runnable, Integer>> tasksAndPriorities = new ArrayList<>();
		final int numTotalTasks = 25;
		final int numTasksWithNullPriority = 5;
		final CountDownLatch countDownLatch = new CountDownLatch(numTotalTasks);

		for (int i = 0; i < numTotalTasks; ++i) {
			final Integer priority = i < numTotalTasks - numTasksWithNullPriority ? i : null;
			final Runnable task = () -> {
				result.add(priority);
				countDownLatch.countDown();
			};
			tasksAndPriorities.add(new ValuePair<>(task, priority));
		}
		Collections.shuffle(tasksAndPriorities);

		final List<Callable<Object>> tasks = new ArrayList<>();
		final List<Integer> priorities = new ArrayList<>();
		for (final Pair<Runnable, Integer> taskAndPriority : tasksAndPriorities) {
			tasks.add(Executors.callable(taskAndPriority.getA()));
			priorities.add(taskAndPriority.getB());
		}
		priorityExecutorService.submitAll(tasks, priorities);

		countDownLatch.await();

		assertEquals(numTotalTasks, result.size());
		for (int i = 0; i < numTotalTasks - numTasksWithNullPriority; ++i)
			assertEquals(i, result.get(i).intValue());
		for (int i = numTotalTasks - numTasksWithNullPriority; i < numTotalTasks; ++i) {
			assertNull(result.get(i));
		}

		priorityExecutorService.shutdown();
		assertTrue(priorityExecutorService.isShutdown());
	}
}
