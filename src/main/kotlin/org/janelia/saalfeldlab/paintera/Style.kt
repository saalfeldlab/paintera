package org.janelia.saalfeldlab.paintera

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ObservableList
import javafx.css.CssMetaData
import javafx.css.StyleOrigin
import javafx.css.Styleable
import javafx.css.StyleableIntegerProperty
import javafx.css.StyleableProperty
import javafx.css.converter.SizeConverter
import javafx.scene.Node
import javafx.scene.layout.Region
import javafx.scene.text.Font
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.kordamp.ikonli.fontawesome.FontAwesome
import org.kordamp.ikonli.javafx.FontIcon
import java.util.Base64
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.reflect.full.memberFunctions


internal object DynamicCssProperty : SimpleStringProperty("") {

	private val LOG = KotlinLogging.logger {  }

	init {
		subscribe { _, new ->
			LOG.debug { "Setting dynamic css to:\n$new" }
		}
	}

	private val prefix = "data:text/css;base64,"

	private val dataUriProperty = map { css -> getDataUri(css) }

	fun append(css : String) = set("$value\n$css")

	fun getDataUri(css : String) : String = "$prefix${Base64.getEncoder().encodeToString(css.toByteArray())}"

	fun register(styleSheets: ObservableList<String>) {
		dataUriProperty.subscribe { old, new ->
			styleSheets.remove(old)
			styleSheets.add(0, new)
		}
		styleSheets.add(0, dataUriProperty.value)
	}
}

interface StyleGroup {

	val classes: Array<out String>

	operator fun plus(style : String) : StyleGroup = of(*classes, style)

	companion object {
		internal fun of(vararg classes: String) = object : StyleGroup {
			override val classes = classes
		}

		internal fun of(vararg style: StyleGroup) = object : StyleGroup {
			override val classes = style.flatMap { it.classes.toList() }.toTypedArray()
		}
	}
}

enum class Style(val style: String, vararg classes: String) : StyleGroup by StyleGroup.of(style, *classes) {
	IGNORE_DISABLE("ignore-disable"),
	TOOLBAR_CONTROL("toolbar-control"),
	TOOLBAR_GRAPHIC("toolbar-graphic"),
	IMAGE_GRAPHIC("image-graphic"),
	FONT_ICON("font-icon"),
	SAVE_ICON("save", FONT_ICON),
	VISIBILITY_ICON("visibility", FONT_ICON),
	CLOSE_ICON("close", FONT_ICON),
	COPY_ICON("copy", FONT_ICON),
	HELP_ICON("help", FONT_ICON),
	REFRESH_ICON("refresh", FONT_ICON),
	RESET_ICON("reset", FONT_ICON),
	ADD_ICON("add", FONT_ICON),
	REMOVE_ICON("remove", FONT_ICON),
	ACCEPT_ICON("accept", FONT_ICON),
	REJECT_ICON("reject", FONT_ICON, IGNORE_DISABLE);



	constructor(style: String, vararg styles: StyleGroup) : this(style, *styles.flatMap { it.classes.toList() }.toTypedArray())
	constructor(style: String) : this(style, *emptyArray<StyleGroup>())


	companion object {

		fun fontAwesome(icon : FontAwesome) : StyleGroup {
			val name = icon.description.removePrefix("fa-")
			return StyleGroup.of(name, *FONT_ICON.classes).also {
				val dynamicCssRule = """
						.$name > .ikonli-font-icon {
							-fx-icon-code: "${icon.description}";
						}
						.$name.ikonli-font-icon {
							-fx-icon-code: "${icon.description}";
						}
					""".trimIndent().trimStart()
				if (!DynamicCssProperty.value.contains(dynamicCssRule))
					DynamicCssProperty.append(dynamicCssRule)
			}
		}

		operator fun ObservableList<String>.plusAssign(style: StyleGroup) {
			addAll(style.classes)
		}
	}
}

private val toolGraphicStyleListener : Consumer<Node?> = Consumer { graphic : Node? ->
	graphic?.let {
		if (!graphic.styleClass.contains(Style.TOOLBAR_GRAPHIC.style))
			graphic.styleClass += Style.TOOLBAR_GRAPHIC.style
	}
}

private fun Styleable.findGraphicProperty() : ObjectProperty<Node?>? = runCatching {
	this::class.memberFunctions
		.find { it.name == "graphicProperty" }
		?.call(this) as? ObjectProperty<Node?>
}.getOrNull()

private fun Styleable.injectGraphicByStyle(graphicProperty: ObjectProperty<Node?>) {
	var graphic by graphicProperty.nullable()
	when {
		graphic != null -> Unit
		Style.FONT_ICON.style in styleClass -> graphic = FontIconPatched()
		Style.TOOLBAR_CONTROL.style in styleClass -> graphic = Region()
		else -> Unit
	}
	graphic?.mouseTransparentProperty()?.set(true)
	if (Style.TOOLBAR_CONTROL.style in styleClass)
		graphicProperty.subscribe(toolGraphicStyleListener)
}

fun Styleable.addStyleClass(vararg style: String) {
	styleClass.addAll(style)
	//TODO: Try to use Skins instead of injecting graphics nodes. See toolbar.css
	this.findGraphicProperty()?.let { injectGraphicByStyle(it) }
}

fun Styleable.addStyleClass(vararg style: StyleGroup) {
	addStyleClass(*style.flatMap { it.classes.toList() }.toTypedArray())
}

//TODO: Try to use Skins instead of injecting graphics nodes. See toolbar.css
//class RegionGraphicButtonSkin(buttonBase: ButtonBase?) : SkinBase<ButtonBase>(buttonBase) {
//
//	private var button: ButtonBase? = buttonBase
//	private var delegateSkin : Skin<*>? = null
//
//	init {
//		println("region")
//	}
//
//	override fun install() {
//		val delegate = when (button) {
//			null -> throw IllegalArgumentException("Skin has already been disposed. ")
//			is ToggleButton -> ToggleButtonSkin(button as ToggleButton)
//			is Button -> ButtonSkin(button as Button)
//			else -> throw IllegalArgumentException("Unsupported labeled type: ${button!!::class.simpleName}")
//		} as Skin<ButtonBase>
//
//		button!!.skin = object : Skin<ButtonBase> by delegate {
//			override fun install() {
//				delegate.install()
//				button!!.apply {
//					graphicProperty()?.unbind()
//					graphic = Region()
//				}
//			}
//		}
//	}
//
//	override fun dispose() {
//		button = null
//		delegateSkin?.dispose()
//		super.dispose()
//	}
//}
//
//class FontIconGraphicButtonSkin(buttonBase: ButtonBase?) : SkinBase<ButtonBase>(buttonBase) {
//
//	private var button: ButtonBase? = buttonBase
//	private var delegateSkin : Skin<*>? = null
//
//	init {
//		println("font-icon")
//	}
//
//	override fun install() {
//		val delegate = when (button) {
//			null -> throw IllegalArgumentException("Skin has already been disposed. ")
//			is ToggleButton -> ToggleButtonSkin(button as ToggleButton)
//			is Button -> ButtonSkin(button as Button)
//			else -> throw IllegalArgumentException("Unsupported labeled type: ${button!!::class.simpleName}")
//		} as Skin<ButtonBase>
//
//		button!!.skin = object : Skin<ButtonBase> by delegate {
//			override fun install() {
//				delegate.install()
//				button!!.apply {
//					graphicProperty()?.unbind()
//					graphic = FontIconPatched()
//				}
//			}
//		}
//	}
//
//	override fun dispose() {
//		button = null
//		delegateSkin?.dispose()
//		super.dispose()
//	}
//}

/* Patched to fix this issue: https://github.com/kordamp/ikonli/issues/150 as of (6/9/2025) */
private class FontIconPatched() : FontIcon() {

	companion object {

		private const val FX_ICON_SIZE = "-fx-icon-size"
		private const val EPSILON = 0.000001
		private val ICON_SIZE =
			object : CssMetaData<FontIcon, Number>(FX_ICON_SIZE, SizeConverter.getInstance(), 8) {
				override fun isSettable(styleable: FontIcon) = true
				override fun getStyleableProperty(styleable: FontIcon) = styleable.iconSizeProperty() as StyleableProperty<Number>
			}

		private fun newIconSizeProperty() = object : StyleableIntegerProperty(8) {
			override fun getBean() = this
			override fun getName() = "iconSize"
			override fun getCssMetaData() = ICON_SIZE
			override fun getStyleOrigin() = StyleOrigin.USER_AGENT
		}

		private val PATCHED_CSS_METADATA = getClassCssMetaData().toMutableList().apply {
			removeIf { it.property == FX_ICON_SIZE }
			add(ICON_SIZE)
		}.toList()

	}

	override fun getCssMetaData() = PATCHED_CSS_METADATA

	override fun iconSizeProperty(): IntegerProperty {
		iconSize = iconSize ?: newIconSizeProperty().also { property ->
			property.subscribe { _, iconSize ->
				font.size - iconSize.toDouble()
				if (abs(font.size - iconSize.toDouble()) >= EPSILON) {
					font = Font.font(font.family, iconSize.toDouble())
				}
			}
		}
		return iconSize!!
	}
}