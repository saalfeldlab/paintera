package org.janelia.saalfeldlab.paintera.control;

import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class ShapeInterpolationMode
{
	private final ObjectProperty<ViewerPanelFX> activeViewer = new SimpleObjectProperty<>();

	private final SelectedIds selectedIds;

	public ShapeInterpolationMode(final SelectedIds selectedIds)
	{
		this.selectedIds = selectedIds;
	}

	public ObjectProperty<ViewerPanelFX> activeViewerProperty()
	{
		return this.activeViewer;
	}

	public EventHandler<Event> modeHandler(final PainteraBaseView paintera, final KeyTracker keyTracker)
	{
		final DelegateEventHandlers.AnyHandler filter = DelegateEventHandlers.handleAny();
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"enter shape interpolation mode",
						e -> enterMode((ViewerPanelFX) e.getTarget()),
						e -> e.getTarget() instanceof ViewerPanelFX &&
							selectedIds.isLastSelectionValid() &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.S)
					)
			);
		return filter;
	}

	public void enterMode(final ViewerPanelFX viewer)
	{
		assert this.activeViewer.get() == null;
		activeViewer.set(viewer);
		setDisableOtherViewers(true);
		// ...
	}

	public void exitMode()
	{
		assert this.activeViewer.get() != null;
		setDisableOtherViewers(false);
		// ...
		this.activeViewer.set(null);
	}

	private void setDisableOtherViewers(final boolean disable)
	{
		final Parent parent = this.activeViewer.get().getParent();
		for (final Node child : parent.getChildrenUnmodifiable())
		{
			if (child instanceof ViewerPanelFX && child != this.activeViewer.get())
			{
				final ViewerPanelFX viewer = (ViewerPanelFX) child;
				viewer.setDisable(disable);
			}
		}
	}
}
