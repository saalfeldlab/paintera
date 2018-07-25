package org.janelia.saalfeldlab.paintera.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import org.janelia.saalfeldlab.fx.event.InstallAndRemove;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;

public class Merges implements ToOnEnterOnExit
{

	private final SourceInfo sourceInfo;

	private final KeyTracker keyTracker;

	private final HashMap<ViewerPanelFX, Collection<InstallAndRemove<Node>>> mouseAndKeyHandlers = new HashMap<>();

	public static String AMBIGUOUS_SELECTION_MESSAGE = "";

	public Merges(
			final SourceInfo sourceInfo,
			final KeyTracker keyTracker)
	{
		this.sourceInfo = sourceInfo;
		this.keyTracker = keyTracker;
	}

	@Override
	public Consumer<ViewerPanelFX> getOnEnter()
	{
		return t -> {
			if (!this.mouseAndKeyHandlers.containsKey(t))
			{
				final IdSelector selector               = new IdSelector(t, sourceInfo);
				final List<InstallAndRemove<Node>> iars = new ArrayList<>();
				iars.add(selector.merge(
						"merge fragments",
						e -> isButton1(e) && keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT)
				                       ));
				iars.add(selector.detach(
						"detach",
						e -> e.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT)
				                        ));
				iars.add(selector.confirm(
						"confirm assignments",
						e -> e.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT, KeyCode.CONTROL)
				                         ));
				this.mouseAndKeyHandlers.put(t, iars);
			}
			t.getDisplay().addHandler(this.mouseAndKeyHandlers.get(t));

		};
	}

	@Override
	public Consumer<ViewerPanelFX> getOnExit()
	{
		return t -> {
			if (this.mouseAndKeyHandlers.containsKey(t))
				t.getDisplay().removeHandler(this.mouseAndKeyHandlers.get(t));
		};
	}

	public static boolean isButton1(final MouseEvent e)
	{
		return e.isPrimaryButtonDown();
	}

	public static boolean isButton2(final MouseEvent e)
	{
		return e.isSecondaryButtonDown();
	}

}
