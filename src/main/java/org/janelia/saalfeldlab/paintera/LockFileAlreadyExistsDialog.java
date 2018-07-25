package org.janelia.saalfeldlab.paintera;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockFileAlreadyExistsDialog
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void showDialog(final LockFile.UnableToCreateLock exception)
	{
		final Alert alert = new Alert(AlertType.ERROR);
		alert.setTitle("Paintera");
		alert.setHeaderText("Unable to create lock file.");

		final Label instructions = new Label(
				"If no other Paintera instance is accessing the same project please delete lock file and try to " +
						"restart Paintera.");
		instructions.setWrapText(true);

		final GridPane  content      = new GridPane();
		final Label     fileLabel    = new Label("Lock File");
		final Label     messageLabel = new Label("Message");
		final TextField fileField    = new TextField(exception.getLockFile().getAbsolutePath());
		final TextField messageField = new TextField(exception.getMessage());
		fileField.setTooltip(new Tooltip(fileField.getText()));
		messageField.setTooltip(new Tooltip(messageField.getText()));

		fileField.setEditable(false);
		messageField.setEditable(false);

		GridPane.setHgrow(fileField, Priority.ALWAYS);
		GridPane.setHgrow(messageField, Priority.ALWAYS);

		content.add(fileLabel, 0, 0);
		content.add(messageLabel, 0, 1);
		content.add(fileField, 1, 0);
		content.add(messageField, 1, 1);

		final VBox contentBox = new VBox(instructions, content);

		alert.getDialogPane().setContent(contentBox);

		final StringWriter stringWriter = new StringWriter();
		exception.printStackTrace(new PrintWriter(stringWriter));
		final String   exceptionText = stringWriter.toString();
		final TextArea textArea      = new TextArea(exceptionText);
		textArea.setEditable(false);
		textArea.setWrapText(true);

		LOG.trace("Exception text (length={}): {}", exceptionText.length(), exceptionText);

		textArea.setMaxWidth(Double.MAX_VALUE);
		textArea.setMaxHeight(Double.MAX_VALUE);
		GridPane.setVgrow(textArea, Priority.ALWAYS);
		GridPane.setHgrow(textArea, Priority.ALWAYS);

		//		final GridPane expandableContent = new GridPane();
		//		expandableContent.setMaxWidth( Double.MAX_VALUE );
		//
		//		expandableContent.add( child, columnIndex, rowIndex );

		alert.getDialogPane().setExpandableContent(textArea);

		//		alert.setResizable( true );
		alert.showAndWait();
	}

}
