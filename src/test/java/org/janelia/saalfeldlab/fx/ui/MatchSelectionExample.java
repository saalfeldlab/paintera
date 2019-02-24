package org.janelia.saalfeldlab.fx.ui;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MatchSelectionExample {

	private static Menu menu(
			final String menuText,
			final Consumer<String> processSelection,
			final List<String> options) {


		final Menu menu = new Menu(menuText);
		final Consumer<String> hideAndProcess = selection -> {
			menu.getParentMenu();
			menu.hide();

			for (Menu m = menu.getParentMenu(); m != null; m = m.getParentMenu())
				m.hide();
			Optional.ofNullable(menu.getParentPopup()).ifPresent(ContextMenu::hide);

			processSelection.accept(selection);
		};
		final MatchSelection fuzzyMatch = MatchSelection.fuzzySorted(options, hideAndProcess, () -> 50);
		fuzzyMatch.setMaxWidth(200);

		final CustomMenuItem cmi = new CustomMenuItem(fuzzyMatch, false);
		menu.setOnShown(e -> fuzzyMatch.requestFocus());
		menu.getItems().setAll(cmi);

		return menu;
	}

	public static void main(String[] args) throws IOException {
		final List<String> filenames = Files.list(Paths.get(System.getProperty("user.home"))).map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.toList());
		Collections.sort(filenames);
		PlatformImpl.startup(() -> {});
		Platform.setImplicitExit(true);
		Platform.runLater(() -> {

			final MenuButton button = new MenuButton("Select!");
			final DirectoryChooser chooser = new DirectoryChooser();
			final MenuItem chooserButton = new MenuItem("Browse");
			chooserButton.setOnAction(e -> chooser.showDialog(null));

			button.getItems().setAll(
					chooserButton,
					MatchSelectionExample.menu("Recent...", System.out::println, filenames),
					MatchSelectionExample.menu("Favorites...", System.out::println, filenames));


			final Stage stage = new Stage();
			final Scene scene = new Scene(button);
			stage.setScene(scene);
			stage.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
				if (KeyCode.ESCAPE.equals(e.getCode())) {
					if (!e.isConsumed())
						stage.close();
					e.consume();
				}
			});
			stage.show();
		});
	}

}
