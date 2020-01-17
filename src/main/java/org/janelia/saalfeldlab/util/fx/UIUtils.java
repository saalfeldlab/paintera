package org.janelia.saalfeldlab.util.fx;

import javafx.beans.value.ChangeListener;
import javafx.scene.control.TextField;

public class UIUtils
{
	/**
	 * Force the field to be numeric only
	 *
	 * @param textField
	 * @param max
	 */
	public static void setNumericTextField(final TextField textField, final int max)
	{
		textField.textProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*"))
			{
				textField.setText(newValue.replaceAll("[^\\d]", ""));
			}

			if (!textField.getText().isEmpty() && Integer.parseInt(textField.getText()) > max)
			{
				textField.setText(Integer.toString(max));
			}
		});
	}
}
