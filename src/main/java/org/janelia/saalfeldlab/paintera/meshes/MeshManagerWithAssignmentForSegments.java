package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Philipp Hanslovsky
 */
public class MeshManagerWithAssignmentForSegments implements MeshManager<Long, TLongHashSet>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<?, ?> source;

	private final InterruptibleFunction<TLongHashSet, Interval[]>[] blockListCache;

	private final InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCache;

	private final FragmentSegmentAssignmentState assignment;

	private final AbstractHighlightingARGBStream stream;

	private final Map<Long, MeshGenerator<TLongHashSet>> neurons = Collections.synchronizedMap(new HashMap<>());

	private final Group root;

	private final SelectedSegments selectedSegments;

	private final FragmentsInSelectedSegments fragmentsInSelectedSegments;

	private final ManagedMeshSettings meshSettings;

	private final ExecutorService managers;

	private final ExecutorService workers;

	private final Runnable refreshMeshes;

	private final BooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

	public MeshManagerWithAssignmentForSegments(
			final DataSource<?, ?> source,
			final InterruptibleFunction<TLongHashSet, Interval[]>[] blockListCacheForFragments,
			final InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCache,
			final Group root,
			final ManagedMeshSettings meshSettings,
			final FragmentSegmentAssignmentState assignment,
			final SelectedSegments selectedSegments,
			final AbstractHighlightingARGBStream stream,
			final Runnable refreshMeshes,
			final ExecutorService managers,
			final ExecutorService workers)
	{
		super();
		this.source = source;
		this.blockListCache = blockListCacheForFragments;
		this.meshCache = meshCache;
		this.root = root;
		this.assignment = assignment;
		this.selectedSegments = selectedSegments;
		this.fragmentsInSelectedSegments = new FragmentsInSelectedSegments(selectedSegments, assignment);
		this.stream = stream;
		this.refreshMeshes = refreshMeshes;

		this.meshSettings = meshSettings;

		this.managers = managers;
		this.workers = workers;

		this.assignment.addListener(obs -> this.update());
		this.selectedSegments.addListener(obs -> this.update());
		this.areMeshesEnabled.addListener((obs, oldv, newv) -> {
			if (newv)
			{
				update();
			}
			else
			{
				removeAllMeshes();
			}
		});

	}

	private void update()
	{
		synchronized (neurons)
		{
			final long[]                                         selectedSegments    = this.selectedSegments
					.getSelectedSegments();
			final TLongHashSet                                   selectedSegmentsSet = new TLongHashSet
					(selectedSegments);
			final Set<Long>                                      currentlyShowing    = new HashSet<>();
			final List<Entry<Long, MeshGenerator<TLongHashSet>>> toBeRemoved         = new ArrayList<>();
			neurons.keySet().forEach(currentlyShowing::add);
			for (final Entry<Long, MeshGenerator<TLongHashSet>> neuron : neurons.entrySet())
			{
				final long         segment            = neuron.getKey();
				final TLongHashSet fragmentsInSegment = assignment.getFragments(segment);
				final boolean      isSelected         = selectedSegmentsSet.contains(segment);
				final boolean      isConsistent       = neuron.getValue().getId().equals(fragmentsInSegment);
				LOG.debug("Fragments in segment {}: {}", segment, fragmentsInSegment);
				LOG.debug("Segment {} is selected? {}  Is consistent? {}", neuron.getKey(), isSelected, isConsistent);
				if (!isSelected || !isConsistent)
				{
					currentlyShowing.remove(neuron.getKey());
					toBeRemoved.add(neuron);
				}

			}
			toBeRemoved.stream().map(e -> e.getValue()).forEach(this::removeMesh);
			LOG.debug("Currently showing {} ", currentlyShowing);
			LOG.debug("Selection {}", selectedSegments);
			LOG.debug("To be removed {}", toBeRemoved);
			Arrays
					.stream(selectedSegments)
					.filter(id -> !currentlyShowing.contains(id))
					.forEach(this::generateMesh);
		}
	}

	@Override
	public void generateMesh(final Long idObject)
	{
		if (!areMeshesEnabled.get())
		{
			LOG.debug("Meshes not enabled -- will return without creating mesh");
			return;
		}
		final long id = idObject;

		if (!this.selectedSegments.isSegmentSelected(id))
		{
			LOG.debug("Id {} not selected -- will return without creating mesh", id);
			return;
		}

		final TLongHashSet fragments = this.assignment.getFragments(id);

		final IntegerProperty color = new SimpleIntegerProperty(stream.argb(id));
		stream.addListener(obs -> color.set(stream.argb(id)));
		assignment.addListener(obs -> color.set(stream.argb(id)));

		final Boolean isPresentAndValid = Optional.ofNullable(neurons.get(idObject)).map(MeshGenerator::getId).map(
				fragments::equals).orElse(false);
		if (isPresentAndValid)
		{
			LOG.warn("Id {} already present with valid selection {}", id, fragments);
		}

		LOG.debug("Adding mesh for segment {}.", id);
		final MeshSettings meshSettings = this.meshSettings.getOrAddMesh(idObject);
		final MeshGenerator<TLongHashSet> nfx = new MeshGenerator<>(
				this.root,
				fragments,
				blockListCache,
				meshCache,
				color,
				meshSettings.scaleLevelProperty().get(),
				meshSettings.simplificationIterationsProperty().get(),
				meshSettings.smoothingLambdaProperty().get(),
				meshSettings.smoothingIterationsProperty().get(),
				managers,
				workers
		);
		final BooleanProperty isManaged = this.meshSettings.isManagedProperty(id);
		isManaged.addListener((obs, oldv, newv) -> nfx.bindTo(newv
		                                                      ? this.meshSettings.getGlobalSettings()
		                                                      : meshSettings));
		nfx.bindTo(isManaged.get() ? this.meshSettings.getGlobalSettings() : meshSettings);

		neurons.put(idObject, nfx);

	}

	@Override
	public void removeMesh(final Long id)
	{
		Optional.ofNullable(unmodifiableMeshMap().get(id)).ifPresent(this::removeMesh);
	}

	private void removeMesh(final MeshGenerator<TLongHashSet> mesh)
	{
		mesh.isEnabledProperty().set(false);
		final List<Long> toRemove = this.neurons
				.entrySet()
				.stream()
				.filter(e -> e.getValue().getId().equals(mesh.getId()))
				.map(Entry::getKey)
				.collect(Collectors.toList());
		toRemove
				.stream()
				.map(this.neurons::remove)
				.filter(n -> n != null)
				.forEach(MeshGenerator::interrupt);
	}

	@Override
	public Map<Long, MeshGenerator<TLongHashSet>> unmodifiableMeshMap()
	{
		return Collections.unmodifiableMap(neurons);
	}

	@Override
	public IntegerProperty scaleLevelProperty()
	{
		return this.meshSettings.getGlobalSettings().scaleLevelProperty();
	}

	@Override
	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSettings.getGlobalSettings().simplificationIterationsProperty();
	}

	@Override
	public void removeAllMeshes()
	{
		final ArrayList<MeshGenerator<TLongHashSet>> generatorsCopy = new ArrayList<>(unmodifiableMeshMap().values());
		generatorsCopy.forEach(this::removeMesh);
	}

	@Override
	public DoubleProperty smoothingLambdaProperty()
	{

		return this.meshSettings.getGlobalSettings().smoothingLambdaProperty();
	}

	@Override
	public IntegerProperty smoothingIterationsProperty()
	{

		return this.meshSettings.getGlobalSettings().smoothingIterationsProperty();
	}

	@Override
	public InterruptibleFunction<TLongHashSet, Interval[]>[] blockListCache()
	{
		return this.blockListCache;
	}

	@Override
	public InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCache()
	{
		return this.meshCache;
	}

	@Override
	public DoubleProperty opacityProperty()
	{
		return this.meshSettings.getGlobalSettings().opacityProperty();
	}

	@Override
	public long[] containedFragments(final Long t)
	{
		return this.assignment.getFragments(t).toArray();
	}

	@Override
	public void refreshMeshes()
	{
		this.refreshMeshes.run();
	}

	@Override
	public BooleanProperty areMeshesEnabledProperty()
	{
		return this.areMeshesEnabled;
	}

}
