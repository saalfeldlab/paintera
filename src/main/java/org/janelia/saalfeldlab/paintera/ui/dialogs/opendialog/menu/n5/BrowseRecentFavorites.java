package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5;

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

	  final MatchSelectionMenu recentMatcher = new MatchSelectionMenu("_Recent", recent, processSelected);
	  recentMatcher.setMaxWidth(400.0);
	  final MatchSelectionMenu favoritesMatcher = new MatchSelectionMenu("_Favorites", favorites, processSelected);
	  favoritesMatcher.setMaxWidth(400.0);

	  browseFoldersButton.setOnAction(onBrowseFoldersClicked);
	  browseFilesButton.setOnAction(onBrowseFilesClicked);
	  recentMatcher.setDisable(recent.size() == 0);
	  favoritesMatcher.setDisable(favorites.size() == 0);
	  return new MenuButton(name, null, browseFoldersButton, browseFilesButton, recentMatcher, favoritesMatcher);
  }

}
