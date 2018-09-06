package org.janelia.saalfeldlab.fx.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

public class Exceptions {

	public static Alert exceptionAlert(
			final String title,
			final String headerText,
			Exception e
	)
	{
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle(title);
		alert.setHeaderText(headerText);
		alert.setContentText(String.format("%s", e.getMessage()));

		// Create expandable Exception.
		StringWriter stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		e.printStackTrace(printWriter);
		String exceptionText = stringWriter.toString();

		Label label = new Label("Stack trace:");

		TextArea textArea = new TextArea(exceptionText);
		textArea.setEditable(false);
		textArea.setWrapText(true);

		textArea.setMaxWidth(Double.MAX_VALUE);
		textArea.setMaxHeight(Double.MAX_VALUE);
		GridPane.setVgrow(textArea, Priority.ALWAYS);
		GridPane.setHgrow(textArea, Priority.ALWAYS);

		GridPane grid = new GridPane();
		grid.setMaxWidth(Double.MAX_VALUE);
		grid.add(label, 0, 0);
		grid.add(textArea, 0, 1);

		// Set expandable Exception into the dialog pane.
		alert.getDialogPane().setExpandableContent(grid);

		// workaround to make resize work properly
		// https://stackoverflow.com/a/30805637/1725687
		alert.getDialogPane().expandedProperty().addListener((l) -> {
			Platform.runLater(() -> {
				alert.getDialogPane().requestLayout();
				Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
				stage.sizeToScene();
			});
		});

		return alert;

	}

	public static Consumer<Exception> handler(
			final String title,
			final String headerText
	                                         )
	{
		return e -> exceptionAlert(title, headerText, e);
	}

}
