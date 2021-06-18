package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import org.janelia.saalfeldlab.fx.ui.MatchSelection;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class BrowseRecentFavorites {

  private static Menu matcherAsMenu(
		  final String menuText,
		  final List<String> candidates,
		  final Consumer<String> processSelection) {

	final Menu menu = new Menu(menuText);
	final Consumer<String> hideAndProcess = selection -> {
	  menu.getParentMenu();
	  menu.hide();

	  for (Menu m = menu.getParentMenu(); m != null; m = m.getParentMenu()) {
		m.hide();
	  }
	  Optional.ofNullable(menu.getParentPopup()).ifPresent(ContextMenu::hide);

	  processSelection.accept(selection);
	};
	final MatchSelection matcher = MatchSelection.fuzzySorted(candidates, hideAndProcess);
	matcher.setMaxWidth(400);

	final CustomMenuItem cmi = new CustomMenuItem(matcher, false);
	menu.setOnShowing(e -> {
	  Platform.runLater(matcher::requestFocus);
	});
	// clear style to avoid weird blue highlight
	cmi.getStyleClass().clear();
	menu.getItems().setAll(cmi);

	return menu;
  }

  public static MenuButton menuButton(
		  final String name,
		  final List<String> recent,
		  final List<String> favorites,
		  final EventHandler<ActionEvent> onBrowseButtonClicked,
		  final Consumer<String> processSelected
  ) {

	final MenuItem browseButton = new MenuItem("_Browse");
	final Menu recentMatcher = matcherAsMenu("_Recent", recent, processSelected);
	final Menu favoritesMatcher = matcherAsMenu("_Favorites", favorites, processSelected);
	browseButton.setOnAction(onBrowseButtonClicked);
	recentMatcher.setDisable(recent.size() == 0);
	favoritesMatcher.setDisable(favorites.size() == 0);
	final MenuButton menuButton = new MenuButton(name, null, browseButton, recentMatcher, favoritesMatcher);
	return menuButton;
  }

}
