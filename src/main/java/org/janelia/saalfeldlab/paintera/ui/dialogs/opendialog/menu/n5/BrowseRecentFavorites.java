package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.janelia.saalfeldlab.fx.ui.MatchSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class BrowseRecentFavorites {

  private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());

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
	menu.setOnShowing(e -> Platform.runLater(matcher::requestFocus));
	// clear style to avoid weird blue highlight
	cmi.getStyleClass().clear();
	try {
	  /* TODO: This should move to saalfx, to fix the highlighting issue. */
	  final VBox matcherVbox = (VBox)matcher.getChildren().get(0);
	  final VBox labelListVbox = (VBox)matcherVbox.getChildren().get(2);
	  final ObservableList<Node> labels = labelListVbox.getChildren();
	  labels.forEach(node -> {
		if (node instanceof Label) {
		  final var label = (Label)node;
		  label.setTextFill(Color.BLACK);
		}
	  });
	} catch (Exception e) {
	  LOG.debug("This should be moved to saalfx");
	}
	menu.getItems().setAll(cmi);

	return menu;
  }

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
	final Menu recentMatcher = matcherAsMenu("_Recent", recent, processSelected);
	final Menu favoritesMatcher = matcherAsMenu("_Favorites", favorites, processSelected);
	browseFoldersButton.setOnAction(onBrowseFoldersClicked);
	browseFilesButton.setOnAction(onBrowseFilesClicked);
	recentMatcher.setDisable(recent.size() == 0);
	favoritesMatcher.setDisable(favorites.size() == 0);
	return new MenuButton(name, null, browseFoldersButton, browseFilesButton, recentMatcher, favoritesMatcher);
  }

}
