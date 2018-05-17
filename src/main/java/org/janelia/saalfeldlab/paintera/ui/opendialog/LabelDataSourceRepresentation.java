package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.util.function.LongFunction;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;

import net.imglib2.Interval;
import net.imglib2.converter.Converter;
import net.imglib2.type.logic.BoolType;
import net.imglib2.util.Pair;

public class LabelDataSourceRepresentation< D, T >
{

	public final DataSource< D, T > source;

	public final FragmentSegmentAssignmentState assignment;

	public final IdService idService;

	final ToIdConverter toIdConverter;

	final InterruptibleFunction< Long, Interval[] >[] blocksThatContainId;

	final InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache;

	final LongFunction< Converter< D, BoolType > > maskForId;

	public LabelDataSourceRepresentation(
			final DataSource< D, T > source,
			final FragmentSegmentAssignmentState assignment,
			final IdService idService,
			final ToIdConverter toIdConverter,
			final InterruptibleFunction< Long, Interval[] >[] blocksThatContainId,
			final InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache,
			final LongFunction< Converter< D, BoolType > > maskForId )
	{
		super();
		this.source = source;
		this.assignment = assignment;
		this.idService = idService;
		this.toIdConverter = toIdConverter;
		this.blocksThatContainId = blocksThatContainId;
		this.meshCache = meshCache;
		this.maskForId = maskForId;
	}

}
