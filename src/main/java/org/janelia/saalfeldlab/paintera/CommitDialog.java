package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.paintera.control.CommitChanges;
import org.janelia.saalfeldlab.paintera.control.CommitChanges.Commitable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitDialog
		implements Function<Collection<CommitChanges.Commitable>, Optional<Set<CommitChanges.Commitable>>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final String headerText;

	private final String contentText;

	public CommitDialog()
	{
		this(
				"Commit to backend.",
				"Select properties to commit to backend:"
		    );
	}

	public CommitDialog(final String headerText, final String contentText)
	{
		super();
		this.headerText = headerText;
		this.contentText = contentText;
	}

	@Override
	public Optional<Set<Commitable>> apply(final Collection<Commitable> commitableOptions)
	{
		final List<Commitable> commitables = new ArrayList<>(commitableOptions);
		Collections.sort(commitables);

		LOG.debug("Selecting from commitables: {}", commitables);

		final Map<Commitable, CheckBox> checkBoxesMap = new HashMap<>();
		final List<CheckBox>            checkBoxes    = new ArrayList<>();

		commitables.forEach(c -> {
			final CheckBox checkBox = new CheckBox(c.toString());
			checkBox.setSelected(true);
			checkBoxes.add(checkBox);
			checkBoxesMap.put(c, checkBox);
		});

		final Dialog<Set<Commitable>> dialog = new Dialog<>();

		dialog.setTitle("Paintera");
		dialog.setHeaderText("Commit to backend.");
		dialog.setContentText("Select properties to commit to backend:");
		dialog.setResizable(true);

		dialog.getDialogPane().setContent(new VBox(checkBoxes.toArray(new CheckBox[checkBoxes.size()])));

		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setResultConverter(bt -> {
			if (ButtonType.OK.equals(bt))
			{
				return commitables
						.stream()
						.filter(c -> checkBoxesMap.get(c).isSelected())
						.collect(Collectors.toSet());
			}
			return null;
		});

		return dialog.showAndWait();
	}

}
