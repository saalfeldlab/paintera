package org.janelia.saalfeldlab.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Optional;

public class TitledPanes
{

	public static Builder builder() {
		return new Builder();
	}

	public static TitledPane createCollapsed(String title, Node contents)
	{
		final TitledPane tp = new TitledPane(title, contents);
		tp.setExpanded(false);
		return tp;
	}

	public static class Builder {

		private String title = null;
		private Boolean isExpanded = null;
		private Node graphic = null;
		private Insets padding = null;
		private Node content = null;
		private Pos alignment = null;

		private Builder() {}

		public Builder withTitle(final String title) {
			this.title = title;
			return this;
		}

		public Builder setIsExpanded(final Boolean isExpanded) {
			this.isExpanded = isExpanded;
			return this;
		}

		public Builder collapsed() {
			return setIsExpanded(false);
		}

		public Builder withGraphic(final Node graphic) {
			this.graphic = graphic;
			return this;
		}

		public Builder withPadding(final Insets padding) {
			this.padding = padding;
			return this;
		}

		public Builder zeroPadding() {
			return withPadding(Insets.EMPTY);
		}

		public Builder withContent(final Node content) {
			this.content = content;
			return this;
		}

		public Builder withAlignment(final Pos alignment) {
			this.alignment = alignment;
			return this;
		}

		public TitledPane build() {
			final TitledPane pane = new TitledPane(title, content);
			Optional.ofNullable(isExpanded).ifPresent(pane::setExpanded);
			Optional.ofNullable(graphic).ifPresent(pane::setGraphic);
			Optional.ofNullable(padding).ifPresent(pane::setPadding);
			Optional.ofNullable(alignment).ifPresent(pane::setAlignment);
			return pane;
		}

	}

	public static TitledPane graphicsOnly(
			final TitledPane pane,
			final String title,
			final Node right) {
		return graphicsOnly(pane, new Label(title), right);
	}

	public static TitledPane graphicsOnly(
			final TitledPane pane,
			final Node left,
			final Node right) {
		final Region spacer = new Region();
		spacer.setMaxWidth(Double.POSITIVE_INFINITY);
		HBox.setHgrow(spacer, Priority.ALWAYS);
		final HBox graphicsContents = new HBox(left, spacer, right);
		graphicsContents.setAlignment(Pos.CENTER);
		graphicsContents.setPadding(new Insets(0, 35, 0, 0));
		return graphicsOnly(pane, graphicsContents);
	}

	public static TitledPane graphicsOnly(
			final TitledPane pane,
			final Pane graphicsContentPane) {
		graphicsContentPane.minWidthProperty().bind(pane.widthProperty());
		pane.setGraphic(graphicsContentPane);
		pane.setText(null);
		return pane;
	}

}
