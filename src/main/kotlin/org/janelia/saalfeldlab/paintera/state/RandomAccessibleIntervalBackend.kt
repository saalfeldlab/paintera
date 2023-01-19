package org.janelia.saalfeldlab.paintera.state

import bdv.util.volatiles.SharedQueue
import bdv.util.volatiles.VolatileTypeMatcher
import javafx.scene.Node
import javafx.scene.control.Label
import net.imglib2.RandomAccessibleInterval
import net.imglib2.Volatile
import net.imglib2.cache.Invalidate
import net.imglib2.converter.Converters
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.Type
import net.imglib2.type.numeric.RealType
import net.imglib2.util.Util
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import java.util.function.Predicate

private val NO_OP_INVALIDATE: Invalidate<Long> = object : Invalidate<Long> {
	override fun invalidate(key: Long) {}
	override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<Long>) {}
	override fun invalidateAll(parallelismThreshold: Long) {}
}

abstract class  RandomAccessibleIntervalBackend<D, T>(
	override val name: String,
	val source: RandomAccessibleInterval<D>,
	val resolution: DoubleArray,
	val offset: DoubleArray
) : SourceStateBackend<D, T>
		where D : RealType<D>, D : NativeType<D>, T : Volatile<D>, T : Type<T> {
	override fun createSource(queue: SharedQueue, priority: Int, name: String): DataSource<D, T> {

		val mipmapTransform = AffineTransform3D()
		mipmapTransform.set(
			resolution[0], 0.0, 0.0, offset[0],
			0.0, resolution[1], 0.0, offset[1],
			0.0, 0.0, resolution[2], offset[2]
		)

		val zeroMinSource = if (Views.isZeroMin(source)) source else Views.zeroMin(source)

		val volatileType = VolatileTypeMatcher.getVolatileTypeForType(Util.getTypeFromInterval(source)).createVariable() as T
		volatileType.isValid = true

		val volatileSource = Converters.convert(
			zeroMinSource,
			{ s, t ->
				(t.get() as NativeType<D>).set(s)
			},
			volatileType
		)
		return RandomAccessibleIntervalDataSource(
			zeroMinSource,
			volatileSource,
			mipmapTransform,
			NO_OP_INVALIDATE,
			{ NearestNeighborInterpolatorFactory() },
			{ NearestNeighborInterpolatorFactory() },
			name
		)

	}

	override fun createMetaDataNode(): Node {
		return Label("No Metadata for RAI backed source")
	}

	override fun getMetadataState(): MetadataState {
		throw NotImplementedError("RAI Backend does not have MetadataState")
	}
}