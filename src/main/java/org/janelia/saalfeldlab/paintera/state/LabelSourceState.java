package org.janelia.saalfeldlab.paintera.state;

import java.util.function.Function;
import java.util.function.LongFunction;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;

import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.converter.Converter;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;

public class LabelSourceState< D, T > extends MinimalSourceState< D, T, HighlightingStreamConverter< T > >
{

	private final LongFunction< Converter< D, BoolType > > maskForLabel;

	private final Function< TLongHashSet, Converter< D, BoolType > > segmentMaskGenerator;

	private final FragmentSegmentAssignmentState assignment;

	private final ToIdConverter toIdConverter;

	private final SelectedIds selectedIds;

	private final IdService idService;

	private final MeshManager< TLongHashSet > meshManager;

	private final MeshInfos meshInfos;

	public LabelSourceState(
			final DataSource< D, T > dataSource,
			final HighlightingStreamConverter< T > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final FragmentSegmentAssignmentState assignment,
			final ToIdConverter toIdConverter,
			final SelectedIds selectedIds,
			final IdService idService,
			final MeshManager< TLongHashSet > meshManager,
			final MeshInfos meshInfos )
	{
		this(
				dataSource,
				converter,
				composite,
				name,
				PainteraBaseView.equalsMaskForType( dataSource.getDataType() ),
				SegmentMaskGenerators.forType( dataSource.getDataType() ),
				assignment,
				toIdConverter,
				selectedIds,
				idService,
				meshManager,
				meshInfos );
	}

	public LabelSourceState(
			final DataSource< D, T > dataSource,
			final HighlightingStreamConverter< T > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final LongFunction< Converter< D, BoolType > > maskForLabel,
			final Function< TLongHashSet, Converter< D, BoolType > > segmentMaskGenerator,
			final FragmentSegmentAssignmentState assignment,
			final ToIdConverter toIdConverter,
			final SelectedIds selectedIds,
			final IdService idService,
			final MeshManager< TLongHashSet > meshManager,
			final MeshInfos meshInfos )
	{
		super( dataSource, converter, composite, name );
		this.maskForLabel = maskForLabel;
		this.segmentMaskGenerator = segmentMaskGenerator;
		this.assignment = assignment;
		this.toIdConverter = toIdConverter;
		this.selectedIds = selectedIds;
		this.idService = idService;
		this.meshManager = meshManager;
		this.meshInfos = meshInfos;

		assignment.addListener( obs -> stain() );
		selectedIds.addListener( obs -> stain() );
	}

	public ToIdConverter toIdConverter()
	{
		return this.toIdConverter;
	}

	public LongFunction< Converter< D, BoolType > > maskForLabel()
	{
		return this.maskForLabel;
	}

	public MeshManager< TLongHashSet > meshManager()
	{
		return this.meshManager;
	}

	public MeshInfos< TLongHashSet > meshInfos()
	{
		return this.meshInfos;
	}

	public FragmentSegmentAssignmentState assignment()
	{
		return this.assignment;
	}

	public IdService idService()
	{
		return this.idService;
	}

	public SelectedIds selectedIds()
	{
		return this.selectedIds;
	}

}
