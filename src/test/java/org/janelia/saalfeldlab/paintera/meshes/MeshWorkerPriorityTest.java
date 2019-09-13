package org.janelia.saalfeldlab.paintera.meshes;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MeshWorkerPriorityTest
{
	@Test
	public void test()
	{
		final List<MeshWorkerPriority> expected = new ArrayList<>();
		expected.add(new MeshWorkerPriority(0.0, 1));
		expected.add(new MeshWorkerPriority(0.1, 1));
		expected.add(new MeshWorkerPriority(0.8, 2));
		expected.add(new MeshWorkerPriority(5.1, 1));
		expected.add(new MeshWorkerPriority(Double.POSITIVE_INFINITY, 4));

		final List<MeshWorkerPriority> priorities = new ArrayList<>(expected);
		Collections.shuffle(priorities);
		Collections.sort(priorities);

		Assert.assertEquals(expected.toArray(), priorities.toArray());
	}
}
