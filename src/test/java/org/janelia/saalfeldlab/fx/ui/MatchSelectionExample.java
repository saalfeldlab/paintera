package org.janelia.saalfeldlab.fx.ui;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
		final MatchSelection fuzzyMatch = MatchSelection.fuzzySorted(options, hideAndProcess, 50);
		fuzzyMatch.setMaxWidth(200);

		final CustomMenuItem cmi = new CustomMenuItem(fuzzyMatch, false);
		menu.setOnShown(e -> fuzzyMatch.requestFocus());
		menu.getItems().setAll(cmi);

		return menu;
	}

	private static void menuExample() throws IOException {

		final List<String> filenames = Files.list(Paths.get(System.getProperty("user.home"))).map(Path::toAbsolutePath).map(Path::toString)
				.collect(Collectors.toList());
		Collections.sort(filenames);
		PlatformImpl.startup(() -> {
		});
		Platform.setImplicitExit(true);
		Platform.runLater(() -> {

			final MenuButton button = new MenuButton("Select!");
			final DirectoryChooser chooser = new DirectoryChooser();
			final MenuItem chooserButton = new MenuItem("Browse");
			chooserButton.setOnAction(e -> chooser.showDialog(null));

			button.getItems().setAll(
					chooserButton,
					new MatchSelectionMenu(filenames, "Recent...", System.out::println),
					new MatchSelectionMenu(Collections.emptyList(), "Disabled...", System.out::println),
					new MatchSelectionMenu(filenames, "Favorites...", System.out::println));

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

	public static void gridPaneExample() throws IOException {

		final List<String> filenames = Files.list(Paths.get(System.getProperty("user.home"))).map(Path::toAbsolutePath).map(Path::toString)
				.collect(Collectors.toList());
		Collections.sort(filenames);
		PlatformImpl.startup(() -> {
		});
		Platform.setImplicitExit(true);
		Platform.runLater(() -> {

			GridPane grid = new GridPane();
			grid.add(new Label("asdfasdfasdf"), 0, 0);
			grid.add(new Label("asdfasdfasdf"), 1, 0);
			grid.add(new Label("asdfasdfasdf"), 2, 0);
			grid.add(new Label("asdfasdfasdf"), 3, 0);
			grid.add(new Label("asdfasdfasdf"), 4, 0);

			final VBox vbox = new VBox(new Label("test"));
			VBox.setVgrow(vbox, Priority.ALWAYS);
			vbox.setAlignment(Pos.CENTER);
			grid.add(vbox, 0, 1);
			GridPane.setColumnSpan(vbox, GridPane.REMAINING);
			GridPane.setHgrow(vbox, Priority.ALWAYS);
			GridPane.setVgrow(vbox, Priority.ALWAYS);

			final MatchSelection node = MatchSelection.fuzzyTop(filenames, it -> {}, 20);
			node.maxWidthProperty().bind(grid.widthProperty());
			grid.add(node, 0, 2);
			GridPane.setColumnSpan(node, GridPane.REMAINING);
			GridPane.setRowSpan(node, GridPane.REMAINING);
			GridPane.setHgrow(node, Priority.ALWAYS);
			GridPane.setVgrow(node, Priority.ALWAYS);

			for (int i = 0; i < grid.getColumnCount(); i++) {
				final ColumnConstraints constraint = new ColumnConstraints();
				constraint.setFillWidth(true);
				constraint.setHgrow(Priority.ALWAYS);
				grid.getColumnConstraints().add(constraint);
			}

			final Stage stage = new Stage();
			final Scene scene = new Scene(grid);
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

	public static void main(String[] args) throws IOException {

		gridPaneExample();
	}

}
