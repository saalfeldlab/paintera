package org.janelia.saalfeldlab.paintera.ui.source;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.Buttons;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.data.mask.exception.CannotClearCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;

public class MaskedSourcePane implements BindUnbindAndNodeSupplier
{

	private final MaskedSource<?, ?> maskedSource;

	public MaskedSourcePane(MaskedSource<?, ?> maskedSource)
	{
		this.maskedSource = maskedSource;
	}

	@Override
	public Node get()
	{
		final CheckBox showCanvasCheckBox = new CheckBox("Show Canvas");
		final Button forgetButton = Buttons.withTooltip("Clear Canvas", e -> showForgetAlert());
		showCanvasCheckBox.selectedProperty().bindBidirectional(maskedSource.showCanvasOverBackgroundProperty());
		VBox contents = new VBox(showCanvasCheckBox, forgetButton);
		return TitledPanes.createCollapsed("Canvas", contents);
	}

	private void showForgetAlert()
	{
		final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setResizable(true);
		alert.setTitle(Paintera.NAME);
		alert.setHeaderText("Clear Canvas");
		final TextArea dialogText = new TextArea("Clearing canvas will remove all painted data that have not been committed yet. Proceed?");
		dialogText.setEditable(false);
		dialogText.setWrapText(true);
		alert.getDialogPane().setContent(dialogText);
		((Button)alert.getDialogPane().lookupButton(ButtonType.OK)).setText("Yes");
		((Button)alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("No");
		if (alert.showAndWait().filter(bt -> ButtonType.OK.equals(bt)).isPresent()) {
			try {
				maskedSource.forgetCanvases();
			} catch (CannotClearCanvas e) {
				Exceptions.exceptionAlert(Paintera.NAME, "Unable to clear canvas.", e);
			}
		}
	}


	@Override
	public void bind()
	{

	}

	@Override
	public void unbind()
	{

	}
}
