package org.janelia.saalfeldlab.paintera.ui.dialogs.open;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import javafx.scene.effect.Effect;

import java.util.Optional;

public class NameField {

	private final SimpleObjectProperty<String> errorMessage;

	private final String errorString;

	private final TextField nameField;

	private final Effect errorEffect;

	private final Effect noErrorEffect;

	private final SimpleObjectProperty<Effect> effectProperty = new SimpleObjectProperty<>();

	public NameField(final String name, final String prompt, final Effect errorEffect) {

		this.nameField = new TextField("");
		this.nameField.setPromptText(prompt);
		this.errorEffect = errorEffect;
		this.noErrorEffect = this.nameField.getEffect();
		this.errorString = name + " not specified!";
		this.errorMessage = new SimpleObjectProperty<>(errorString);
		this.nameField.textProperty().addListener((obs, oldv, newv) -> {
			final boolean isError = Optional.ofNullable(newv).orElse("").length() > 0;
			this.errorMessage.set(!isError ? errorString : "");
			this.effectProperty.set(!isError ? this.errorEffect : noErrorEffect);
		});

		this.effectProperty.addListener((obs, oldv, newv) -> {
			if (!nameField.isFocused())
				nameField.setEffect(newv);
		});

		nameField.setEffect(this.effectProperty.get());

		nameField.focusedProperty().addListener((obs, oldv, newv) -> {
			if (newv)
				nameField.setEffect(this.noErrorEffect);
			else
				nameField.setEffect(effectProperty.get());
		});

		this.nameField.setText(null);

	}

	public ObservableValue<String> errorMessageProperty() {

		return this.errorMessage;
	}

	public TextField textField() {

		return this.nameField;
	}

	public String getText() {

		return this.nameField.getText();
	}

}
