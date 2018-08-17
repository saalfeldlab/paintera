package org.janelia.saalfeldlab.fx.ui;

import java.io.File;
import java.util.Optional;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;

public class DirectoryField
{

	private final DirectoryChooser chooser = new DirectoryChooser();

	private final ObjectField<File, Property<File>> directory;

	private final Button browseButton = new Button("Browse");

	private final HBox contents;

	public DirectoryField(final String initialFile, double browseButtonWidth)
	{
		this(new File(initialFile), browseButtonWidth);
	}

	public DirectoryField(final File initialFile, double browseButtonWidth)
	{
		this.directory = ObjectField.fileField(initialFile, d -> d.exists() && d.isDirectory(), ObjectField.SubmitOn.values());
		chooser.initialDirectoryProperty().bind(this.directory.valueProperty());
		browseButton.setPrefWidth(browseButtonWidth);
		HBox.setHgrow(directory.textField(), Priority.ALWAYS);
		this.contents = new HBox(this.directory.textField(), browseButton);
		this.browseButton.setOnAction( e -> {
			e.consume();
			final File d = chooser.showDialog(browseButton.getScene().getWindow());
			Optional.ofNullable(d).ifPresent(directory.valueProperty()::setValue);
		});
	}

	public Node asNode()
	{
		return this.contents;
	}

	public Property<File> directoryProperty()
	{
		return this.directory.valueProperty();
	}

}
