package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.event.Event
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.scopeJob
import org.janelia.saalfeldlab.paintera.control.actions.state.ViewerAndPaintableSourceActionState
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import kotlin.math.floor
import kotlin.math.roundToInt

enum class LabelSelection() {
	ActiveFragments,
	ActiveSegments
}

enum class InfillStrategy() {
	Replace,
	Background,
	NearestLabel

}

enum class SmoothStatus(val text: String) {
	Smoothing("Smoothing... "),
	Done("        Done "),
	Applying(" Applying... "),
	Empty("             ")
}

internal class SmoothLabelState<D, T> :
	ViewerAndPaintableSourceActionState<ConnectomicsLabelState<D, T>, D, T>(),
	SmoothLabelUI.Model by SmoothLabelUI.Default()
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	val resolution by LazyForeignValue(::scaleLevel) { getLevelResolution(it) }
	private val defaultKernelSize: Int
		get() {
			val min = resolution.min()
			val max = resolution.max()
			return (min + (max - min) / 2.0).roundToInt()
		}

	override val minKernelSize
		get() = floor(resolution.min() / 2).toInt()
	override val maxKernelSize
		get() = (resolution.max() * 10).toInt()
	override val kernelSizeProperty: IntegerProperty by lazy { SimpleIntegerProperty(defaultKernelSize) }

	var progress by progressProperty.nonnull()

	override fun <E : Event> verifyState(action: Action<E>) {
		super.verifyState(action)
		action.verify("Mask is in Use") { !this@SmoothLabelState.dataSource.isMaskInUseBinding().get() }
	}

	val dataSource by lazy { paintContext.dataSource }
	private val selectedIds by lazy { paintContext.selectedIds }
	fun refreshMeshes() = paintContext.refreshMeshes()

	//TODO Caleb: Currently, this is always 0
	//  At higher scale levels, currently it can leave small artifacts at the previous boundary when
	//  going back to higher resolution
	internal val scaleLevel = 0

	val labelsToSmooth : LongArray
		get() = when (labelSelectionProperty.get()) {
			LabelSelection.ActiveFragments -> selectedIds.activeIds.toArray()
			LabelSelection.ActiveSegments -> let {
				val fragments = mutableSetOf<Long>()
				with(paintContext.assignment) {
					selectedIds.activeIds.toArray().map { getSegment(it) }.toSet().forEach { getFragments(it).forEach { fragments.add(it) } }
				}
				fragments.toLongArray()
			}
		}

	init {
		initProgresSubscription()
	}

	override fun newId() = sourceState.idService.next()

	private fun initProgresSubscription() {
		runCatching {
			progressProperty.subscribe { progress ->
				val progress = progress.toDouble()
				val isApplyMask = dataSource.isApplyingMaskProperty()
				statusProperty.value = when {
					progress == 0.0 -> SmoothStatus.Empty
					progress == 1.0 -> SmoothStatus.Done
					isApplyMask.get() -> SmoothStatus.Applying
					scopeJob?.isActive == true -> SmoothStatus.Smoothing
					else -> SmoothStatus.Empty
				}
			}
		}
	}

	fun getLevelResolution(level: Int): DoubleArray {

		if (level == 0)
			return sourceState.resolution

		val n5Backend = sourceState.backend as? SourceStateBackendN5<*, *>
		val metadataState = n5Backend?.metadataState as? MultiScaleMetadataState
		val metadataScales = metadataState?.scaleTransforms?.get(level)
		if (metadataScales != null)
			return metadataScales.resolution

		(sourceState.backend as? RandomAccessibleIntervalBackend<*, *>)?.resolutions?.get(level)?.let { resolution ->
			return resolution
		}

		return doubleArrayOf(1.0, 1.0, 1.0)
	}

	companion object {
		private val AffineTransform3D.resolution
			get() = doubleArrayOf(this[0, 0], this[1, 1], this[2, 2])
	}
}