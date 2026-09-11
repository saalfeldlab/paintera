package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5

import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenu

object BrowseRecentFavorites {

	/**
	 * A menu button offering browse actions plus the [recent] and [favorites] lists.
	 *
	 * [onRemoveRecent] adds a remove button to each row of the recents menu; favorites are
	 * read from the Paintera config, so they stay read only.
	 */
	/* TODO Caleb: Maybe a custom component to let you choose files and folders? */
	@JvmStatic
	@JvmOverloads
	fun menuButton(
		name: String,
		recent: List<String>,
		favorites: List<String>,
		onBrowseFoldersClicked: EventHandler<ActionEvent>,
		onBrowseFilesClicked: EventHandler<ActionEvent>,
		onRemoveRecent: ((String) -> Unit)? = null,
		processSelected: (String) -> Unit
	): MenuButton {

		val browseFoldersButton = MenuItem("_Browse Folders").apply { onAction = onBrowseFoldersClicked }
		val browseFilesButton = MenuItem("_Browse Files").apply { onAction = onBrowseFilesClicked }

		val select: (String?) -> Unit = { selection -> selection?.let(processSelected) }
		val recentMatcher = MatchSelectionMenu(recent, "_Recent", 400.0, onRemoveRecent, select)

		val items = mutableListOf(browseFoldersButton, browseFilesButton, recentMatcher)
		if (favorites.isNotEmpty())
			items += MatchSelectionMenu(favorites, "_Favorites", 400.0, null, select)

		return MenuButton(name, null, *items.toTypedArray())
	}
}
