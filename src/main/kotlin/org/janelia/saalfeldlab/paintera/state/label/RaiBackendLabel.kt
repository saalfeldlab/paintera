package org.janelia.saalfeldlab.paintera.state.label

import net.imglib2.RandomAccessibleInterval
import net.imglib2.Volatile
import net.imglib2.type.NativeType
import net.imglib2.type.Type
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.LocalIdService
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks

class RaiBackendLabel<D, T>(
	name: String,
	sources: Array<RandomAccessibleInterval<D>>,
	resolutions: Array<DoubleArray>,
	translations: Array<DoubleArray>,
	val maxId: Long
) : RandomAccessibleIntervalBackend<D, T>(
	name,
	sources,
	resolutions,
	translations
), ConnectomicsLabelBackend<D, T> where D : RealType<D>, D : NativeType<D>, T : Volatile<D>, T : Type<T> {

	constructor(
		name: String,
		source: RandomAccessibleInterval<D>,
		resolution: DoubleArray,
		translation: DoubleArray,
		maxId: Long
	) : this(name, arrayOf(source), arrayOf(resolution), arrayOf(translation), maxId)

	override val fragmentSegmentAssignment = FragmentSegmentAssignmentOnlyLocal(FragmentSegmentAssignmentOnlyLocal.DoesNotPersist())

	override fun createIdService(source: DataSource<D, T>) = LocalIdService(maxId)

	override fun createLabelBlockLookup(source: DataSource<D, T>) = LabelBlockLookupAllBlocks.fromSource(source)

}