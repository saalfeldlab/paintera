package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenu;

import java.util.List;
import java.util.function.Consumer;

public class BrowseRecentFavorites {

	public static MenuButton menuButton(
			final String name,
			final List<String> recent,
			final List<String> favorites,
			final EventHandler<ActionEvent> onBrowseFoldersClicked,
			final EventHandler<ActionEvent> onBrowseFilesClicked,
			final Consumer<String> processSelected
	) {
		/* TODO Caleb: Maybe a custom component to let you choose files or folders? */
		final MenuItem browseFoldersButton = new MenuItem("_Browse Folders");
		final MenuItem browseFilesButton = new MenuItem("_Browse Files");

		browseFoldersButton.setOnAction(onBrowseFoldersClicked);
		browseFilesButton.setOnAction(onBrowseFilesClicked);

		final MatchSelectionMenu recentMatcher = new MatchSelectionMenu(recent, "_Recent", 400.0, processSelected);
		if (!favorites.isEmpty()) {
			final MatchSelectionMenu favoritesMatcher = new MatchSelectionMenu(favorites, "_Favorites", 400.0, processSelected);
			return new MenuButton(name, null, browseFoldersButton, browseFilesButton, recentMatcher, favoritesMatcher);
		} else {
			return new MenuButton(name, null, browseFoldersButton, browseFilesButton, recentMatcher);
		}

	}

}
