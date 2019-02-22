package org.janelia.saalfeldlab.fx.ui;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FuzzyMatchSelection {

	private final Collection<String> options;

	private final StringProperty currentSelection = new SimpleStringProperty(this, "currentSelection", null);

	public FuzzyMatchSelection(final Collection<String> options) {
		this.options = options;
	}

	public ReadOnlyStringProperty currentSelectionProperty() {
		return currentSelection;
	}

	public String cetCurrentSelection() {
		return currentSelectionProperty().getValue();
	}

	public VBox matcherNode() {

		final TextField fuzzySearchField = new TextField(null);
		final ObservableList<String> currentOrder = FXCollections.observableArrayList();
		final ListView<String> choices = new ListView<>(currentOrder);
		fuzzySearchField.textProperty().addListener((obs, oldv, newv) -> currentOrder.setAll(fuzzyMatch(newv, options)));
		fuzzySearchField.textProperty().addListener((obs, oldv, newv) -> currentSelection.setValue(currentOrder.size() > 0 ? currentOrder.get(0) : null));
		fuzzySearchField.setText("");
		final VBox vbox = new VBox(choices, fuzzySearchField);

		fuzzySearchField.textProperty().addListener(observable -> choices.getSelectionModel().select(-1));

		choices.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		choices.getSelectionModel().selectedIndexProperty().addListener((obs, oldv, newv) -> {
			final List<String> orderCopy = new ArrayList<>(currentOrder);
			final int index = newv.intValue() < 0 || newv.intValue() >= orderCopy.size() ? 0 : newv.intValue();
			if (index >= 0 && index < currentOrder.size())
				currentSelection.setValue(currentOrder.get(index));
		});

		fuzzySearchField.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			switch (e.getCode()) {
				case ESCAPE:
					final String text = fuzzySearchField.getText();
					System.out.println("FIELD TEXT " + text + " OK " + text.length());
					if (text != null && text.length() > 0) {
						fuzzySearchField.setText("");
						e.consume();
					}
					break;
				default:
					break;
			}
		});

		choices.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			switch (e.getCode()) {
				case SHIFT:
				case CONTROL:
				case META:
				case WINDOWS:
					break;
				case BACK_SPACE:
					fuzzySearchField.setText(stripLast(fuzzySearchField.getText()));
					e.consume();
					break;
				case ESCAPE:
					final String text = fuzzySearchField.getText();
					if (text != null && text.length() > 0) {
						fuzzySearchField.setText("");
						e.consume();
					}
					break;
				default:
					final String eventText = e.getText();
					if (!eventText.isEmpty()) {
						fuzzySearchField.setText(fuzzySearchField.getText() + eventText);
						e.consume();
					}
					break;
			}
		});

		return vbox;
	}

	private static List<String> fuzzyMatch(final String query, final Collection<String> from) {
		return FuzzySearch
				.extractSorted(query, from)
				.stream()
				.map(ExtractedResult::getString)
				.collect(Collectors.toList());
	}

	private static String stripLast(final String string) {
		return string == null ? null : string.substring(0, Math.max(string.length() - 1, 0));
	}

	public static void main(String[] args) throws IOException {
		final List<String> lines = Files.readAllLines(Paths.get(System.getProperty("user.home"), ".zsh_history"));
		Collections.shuffle(lines);
		final List<String> subSet = lines.subList(0, 200);
		PlatformImpl.startup(() -> {});
		Platform.setImplicitExit(true);
		Platform.runLater(() -> {

			final MenuButton button = new MenuButton("Select!");



			final DirectoryChooser chooser = new DirectoryChooser();
			final FuzzyMatchSelection fms1 = new FuzzyMatchSelection(subSet);
			final FuzzyMatchSelection fms2 = new FuzzyMatchSelection(subSet);

			final ObjectProperty<File> currentSelection = new SimpleObjectProperty<>(null);
			currentSelection.addListener((obs, oldv, newv) -> System.out.println("Setting to " + newv));
			fms1.currentSelectionProperty().addListener((obs, oldv, newv) -> currentSelection.setValue(new File(newv)));
			fms2.currentSelectionProperty().addListener((obs, oldv, newv) -> currentSelection.setValue(new File(newv)));

			final MenuItem chooserButton = new MenuItem("Browse");
			final MenuItem recent = new MenuItem(null, fms1.matcherNode());
			final MenuItem favorites = new MenuItem(null, fms1.matcherNode());

			chooserButton.setOnAction(e -> currentSelection.set(chooser.showDialog(null)));

			button.getItems().setAll(
					chooserButton,
					new Menu("Recent Containers", null, new CustomMenuItem(fms1.matcherNode(), false)),
					new Menu("Favorite containers", null, new CustomMenuItem(fms2.matcherNode(), false)));


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
