package org.janelia.saalfeldlab.paintera.meshes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class MeshWorkerPriorityTest {

	@Test
	public void test() {

		final List<MeshWorkerPriority> expected = new ArrayList<>();
		expected.add(new MeshWorkerPriority(0.0, 4));
		expected.add(new MeshWorkerPriority(0.1, 3));
		expected.add(new MeshWorkerPriority(0.1, 0));
		expected.add(new MeshWorkerPriority(0.8, 5));
		expected.add(new MeshWorkerPriority(5.1, 1));
		expected.add(new MeshWorkerPriority(Double.POSITIVE_INFINITY, 0));

		final List<MeshWorkerPriority> priorities = new ArrayList<>(expected);
		Collections.shuffle(priorities);
		Collections.sort(priorities);

		assertArrayEquals(expected.toArray(), priorities.toArray());
	}
}
