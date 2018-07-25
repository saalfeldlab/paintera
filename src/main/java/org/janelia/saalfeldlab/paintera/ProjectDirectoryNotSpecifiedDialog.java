package org.janelia.saalfeldlab.paintera;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import org.janelia.saalfeldlab.paintera.data.mask.TmpDirectoryCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectDirectoryNotSpecifiedDialog
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final boolean defaultToTempDirectory;

	public ProjectDirectoryNotSpecifiedDialog(final boolean defaultToTempDirectory)
	{
		super();
		this.defaultToTempDirectory = defaultToTempDirectory;
	}

	public Optional<String> showDialog(final String contentText) throws ProjectDirectoryNotSpecified
	{

		if (this.defaultToTempDirectory) { return Optional.of(tmpDir()); }

		final StringProperty projectDirectory = new SimpleStringProperty(null);

		final ButtonType specifyProject = new ButtonType("Specify Project", ButtonData.OTHER);
		final ButtonType noProject      = new ButtonType("No Project", ButtonData.OK_DONE);

		final Dialog<String> dialog = new Dialog<>();
		dialog.setResultConverter(bt -> {
			return ButtonType.CANCEL.equals(bt)
			       ? null
			       : noProject.equals(bt)
			         ? tmpDir()
			         : projectDirectory.get();
		});

		dialog.getDialogPane().getButtonTypes().setAll(specifyProject, noProject, ButtonType.CANCEL);
		dialog.setTitle("Paintera");
		dialog.setHeaderText("Specify Project Directory");
		dialog.setContentText(contentText);

		final Node lookupProjectButton = dialog.getDialogPane().lookupButton(specifyProject);
		if (lookupProjectButton instanceof Button)
		{
			((Button) lookupProjectButton).setTooltip(new Tooltip("Look up project directory."));
		}
		Optional
				.ofNullable(dialog.getDialogPane().lookupButton(noProject))
				.filter(b -> b instanceof Button)
				.map(b -> (Button) b)
				.ifPresent(b -> b.setTooltip(new Tooltip("Create temporary project in /tmp.")));
		Optional
				.ofNullable(dialog.getDialogPane().lookupButton(ButtonType.CANCEL))
				.filter(b -> b instanceof Button)
				.map(b -> (Button) b)
				.ifPresent(b -> b.setTooltip(new Tooltip("Do not start Paintera.")));

		lookupProjectButton.addEventFilter(ActionEvent.ACTION, event -> {
			final DirectoryChooser chooser = new DirectoryChooser();
			final Optional<String> d       = Optional.ofNullable(chooser.showDialog(dialog.getDialogPane().getScene()
					.getWindow())).map(
					File::getAbsolutePath);
			if (d.isPresent())
			{
				projectDirectory.set(d.get());
			}
			else
			{
				// consume on cancel, so that parent dialog does not get closed.
				event.consume();
			}
		});

		dialog.setResizable(true);

		final Optional<String> returnVal = dialog.showAndWait();

		if (!returnVal.isPresent()) { throw new ProjectDirectoryNotSpecified(); }

		return returnVal;

	}

	private static String tmpDir()
	{
		// TODO read tmp directory and prefix from ~/.paintera/config if present
		final String tmpDir = new TmpDirectoryCreator(null, "paintera-project-").get();
		LOG.info("Using temporary project directory {}", tmpDir);
		return tmpDir;
	}

}
