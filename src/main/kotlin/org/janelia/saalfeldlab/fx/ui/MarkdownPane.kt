package org.janelia.saalfeldlab.fx.ui

import com.sun.javafx.application.PlatformImpl
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TextArea
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.janelia.saalfeldlab.paintera.ui.RefreshButton

class MarkdownPane() : TabPane() {

	private val edit = TextArea()
	private val rendered = WebView()
	private val editTab = Tab("Edit", edit)
	private val renderedTab = Tab("View", rendered)


	init {
		edit.textProperty().addListener { _, _, new -> if (selectionModel.selectedItem === renderedTab) updateMarkdown() }
		selectionModel.selectedItemProperty().addListener { _, _, new -> if (new === renderedTab) updateMarkdown() }
		this.tabs.addAll(editTab, renderedTab)
		this.tabClosingPolicy = TabClosingPolicy.UNAVAILABLE
		Button(null, RefreshButton.create(scale = 8.0))
				.also { bt -> bt.setOnAction { updateMarkdown() } }
				.let { renderedTab.graphic = it }
		edit.isWrapText = false
	}

	fun showEditTab() = this.selectionModel.select(editTab)

	fun showRenderedTab() = this.selectionModel.select(renderedTab)

	private fun updateMarkdown() = rendered.engine.loadContent(markdownToHtml(text))

	var isEditable: Boolean
		get() = edit.isEditable
		set(isEditable) = edit.setEditable(isEditable)

	var isWrapText: Boolean
		get() = edit.isWrapText
		set(isWrapText) = edit.setWrapText(isWrapText)

	var text: String?
		get() = edit.text
		set(text) = edit.setText(text)

	companion object {

		private val MARKDOWN_EXTENSIONS = listOf(TablesExtension.create())

		private val MARKDOWN_PARSER = Parser.builder().extensions(MARKDOWN_EXTENSIONS).build()

		private val MARKDOWN_RENDERER = HtmlRenderer.builder().extensions(MARKDOWN_EXTENSIONS).build()

		private fun markdownToHtml(
				markdown: String?,
				parser: Parser = MARKDOWN_PARSER,
				renderer: HtmlRenderer = MARKDOWN_RENDERER) = renderer.render(parser.parse(markdown ?: ""))
	}

}
