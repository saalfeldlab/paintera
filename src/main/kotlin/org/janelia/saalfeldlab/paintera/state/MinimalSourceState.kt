package org.janelia.saalfeldlab.paintera.state

import bdv.viewer.Interpolation
import bdv.viewer.SourceAndConverter
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import net.imglib2.converter.Converter
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.data.DataSource

open class MinimalSourceState<D, T, S : DataSource<D, T>, C : Converter<T, ARGBType>>(
	private val dataSource: S,
	private val converter: C,
	composite: Composite<ARGBType, ARGBType>,
	name: String,
	vararg dependsOn: SourceState<*, *>
) : SourceState<D, T> {
	private val compositeProperty = SimpleObjectProperty<Composite<ARGBType, ARGBType>>(composite)
	private val nameProperty : StringProperty = SimpleStringProperty(name)
	private val statusTextProperty: StringProperty = SimpleStringProperty(null)
	private val isVisibleProperty: BooleanProperty = SimpleBooleanProperty(true)
	private val interpolation: ObjectProperty<Interpolation> = SimpleObjectProperty<Interpolation>(Interpolation.NEARESTNEIGHBOR)
	private val dependsOn = dependsOn.filter { it != this }.toTypedArray()

	init {
		LOG.debug { "Creating minimal source state with dataSource=${dataSource} converter=${converter} composite=${composite} name=${name} dependsOn=${dependsOn}" }
	}

	override fun compositeProperty() = compositeProperty
	override fun nameProperty() = nameProperty
	override fun converter() = converter
	override fun statusTextProperty() = statusTextProperty
	override fun isVisibleProperty() = this.isVisibleProperty
	override fun interpolationProperty() = this.interpolation
	override fun getSourceAndConverter() = SourceAndConverter<T>(dataSource, converter)
	override fun getDataSource() = this.dataSource
	override fun dependsOn() = dependsOn.clone()

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}