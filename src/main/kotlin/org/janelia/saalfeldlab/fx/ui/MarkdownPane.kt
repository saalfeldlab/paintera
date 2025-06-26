package org.janelia.saalfeldlab.fx.ui

import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TextArea
import javafx.scene.web.WebView
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass

class MarkdownPane() : TabPane() {

	private val edit = TextArea()
	private val rendered = WebView()
	private val editTab = Tab("Edit", edit)
	private val renderedTab = Tab("View", rendered)


	init {
		edit.textProperty().addListener { _, _, _ -> if (selectionModel.selectedItem === renderedTab) updateMarkdown() }
		selectionModel.selectedItemProperty().addListener { _, _, new -> if (new === renderedTab) updateMarkdown() }
		this.tabs.addAll(editTab, renderedTab)
		this.tabClosingPolicy = TabClosingPolicy.UNAVAILABLE
		renderedTab.addStyleClass(Style.REFRESH_ICON)
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
			renderer: HtmlRenderer = MARKDOWN_RENDERER
		) = renderer.render(parser.parse(markdown ?: ""))
	}

}
