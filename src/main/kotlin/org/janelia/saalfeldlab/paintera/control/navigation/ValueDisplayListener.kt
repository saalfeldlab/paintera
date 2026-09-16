package org.janelia.saalfeldlab.paintera.control.navigation

import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.TransformListener
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import net.imglib2.RealRandomAccessible
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import net.imglib2.view.composite.Composite
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.ChannelLoop
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.data.n5.ChannelCompositeSource
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet
import java.util.function.Consumer
import java.util.function.Function

class ValueDisplayListener<T>(
	private val viewer: ViewerPanelFX,
	currentSource: ObservableValue<Source<*>>,
	interpolation: Function<Source<*>, Interpolation>,
	private val submitValue: Consumer<String>
) : EventHandler<MouseEvent>, TransformListener<AffineTransform3D> {

	private val readScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private val readLoop = ChannelLoop(readScope, Channel.CONFLATED, "value-display-listener")

	private val viewerTransform = AffineTransform3D()
	private val viewerTransformChanged = SimpleBooleanProperty()

	private var x = -1.0
	private var y = -1.0

	@Suppress("UNCHECKED_CAST")
	private val source: ObservableValue<Source<T>?> = currentSource.map { it as Source<T> }

	private val randomAccessibleBinding = Bindings.createObjectBinding<RealRandomAccessible<T>?>(
        {
            source.value?.let { source ->
                val level = viewer.state.getBestMipMapLevel(source)
                val sourceTransform = AffineTransform3D()
                source.getSourceTransform(0, level, sourceTransform)
                val interpolation = interpolation.apply(source)
                val interpolatedSource = source.getInterpolatedSource(0, level, interpolation)
                RealViews.transformReal(interpolatedSource, sourceTransform)
            }
        },
        currentSource,
        viewer.renderUnit.screenScalesProperty,
        viewerTransformChanged,
        viewer.renderUnit.repaintRequestProperty
    )
	private val subscription = randomAccessibleBinding.subscribe { _, accessible ->
		accessible?.let { getInfo(it) }
	}

	override fun handle(event: MouseEvent) {
		if (x == event.x && y == event.y)
			return

		x = event.x
		y = event.y
		randomAccessibleBinding.value?.let { getInfo(it) }
	}

	override fun transformChanged(transform: AffineTransform3D) {
		if (transform.rowPackedCopy.contentEquals(viewerTransform.rowPackedCopy))
			return

		viewerTransform.set(transform)
		viewerTransformChanged.value = !viewerTransformChanged.value
	}

	private fun getInfo(accessible: RealRandomAccessible<T>) {
		val source = source.value ?: return
		val x = this.x
		val y = this.y

		readLoop.submit {
			val value = getValueAt(accessible, x, y)
            val valueInfo = stringConverterFromSource(source)(value)
			InvokeOnJavaFXApplicationThread { submitValue.accept(valueInfo) }
		}
	}

	private fun getValueAt(accessible: RealRandomAccessible<T>, x: Double, y: Double): T {
		val access = accessible.realRandomAccess()
		viewer.displayToGlobalCoordinates(x, y, access)
		return access.get()
	}

	fun dispose() {
		subscription.unsubscribe()
		readScope.cancel()
	}

	companion object {

		private fun <T> stringConverterFromSource(source: Source<T>): (T) -> String {
			val channelSource = source as? ChannelCompositeSource<*, *> ?: return stringConverter(source.type)

			val numChannels = channelSource.numChannels
			return { value ->
				@Suppress("UNCHECKED_CAST")
				val composite = (value as VolatileWithSet<out Composite<*>>).get()
				val converter = stringConverter(composite.get(0))
				(0 until numChannels).joinToString(", ", "(", ")") {
                    converter(composite.get(it.toLong()))
                }
			}
		}

		private fun <V> stringConverter(value: V): (V) -> String {
			if (value is Volatile<*>)
				return { (it as Volatile<*>).get().toString() }
			return { it.toString() }
		}
	}
}
