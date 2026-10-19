package org.janelia.saalfeldlab.paintera.filters

import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.RenderFilter

/** A [RenderFilterModel] whose filter is [build] from its settings, and `null` while it is disabled */
abstract class ConfigFilterModel<C : RenderFilterConfig, F : RenderFilter>(final override val config: C) : RenderFilterModel, RenderFilterSettings {

	final override val enabledProperty = SimpleBooleanProperty().apply { bindBidirectional(config.enabledProperty) }

	private val _filter = SimpleObjectProperty<F?>()
	final override val filterProperty: ReadOnlyObjectProperty<F?> = _filter

	protected abstract fun build(): F

	/** call once the settings exist: the filter is rebuilt whenever one of [inputs] or [enabledProperty] changes */
	protected fun rebuildOn(vararg inputs: ObservableValue<*>) {
		(inputs.toList() + enabledProperty).forEach { input ->
			input.subscribe { _ -> _filter.set(if (enabledProperty.get()) build() else null) }
		}
	}
}
