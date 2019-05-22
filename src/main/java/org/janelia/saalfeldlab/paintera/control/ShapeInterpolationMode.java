package org.janelia.saalfeldlab.paintera.control;

import java.lang.invoke.MethodHandles;
import java.util.function.Predicate;

import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions;
import org.janelia.saalfeldlab.paintera.control.actions.LabelAction;
import org.janelia.saalfeldlab.paintera.control.actions.NavigationAction;
import org.janelia.saalfeldlab.paintera.control.actions.PaintAction;
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;

public class ShapeInterpolationMode<D extends IntegerType<D>>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final AllowedActions allowedActionsInShapeInterpolationMode;
	static
	{
		allowedActionsInShapeInterpolationMode = new AllowedActions(
			NavigationAction.of(NavigationAction.Drag, NavigationAction.Zoom, NavigationAction.Scroll),
			LabelAction.none(),
			PaintAction.none()
		);
	}

	private static final class ForegroundCheck implements Predicate<UnsignedLongType>
	{
		@Override
		public boolean test(final UnsignedLongType t)
		{
			return t.getIntegerLong() == 1;
		}
	}

	private static final ForegroundCheck FOREGROUND_CHECK = new ForegroundCheck();

	private static final double FILL_DEPTH = 2.0;

	private static final Color MASK_COLOR = Color.web("00CCFF");

	private final ObjectProperty<ViewerPanelFX> activeViewer = new SimpleObjectProperty<>();

	private final MaskedSource<D, ?> source;
	private final SelectedIds selectedIds;
	private final IdService idService;
	private final HighlightingStreamConverter<?> converter;

	private AllowedActions lastAllowedActions;
	private long lastSelectedId;
	private long[] lastActiveIds;

	private Mask<UnsignedLongType> mask;

	public ShapeInterpolationMode(
			final MaskedSource<D, ?> source,
			final SelectedIds selectedIds,
			final IdService idService,
			final HighlightingStreamConverter<?> converter)
	{
		this.source = source;
		this.selectedIds = selectedIds;
		this.idService = idService;
		this.converter = converter;
	}

	public ObjectProperty<ViewerPanelFX> activeViewerProperty()
	{
		return activeViewer;
	}

	public EventHandler<Event> modeHandler(final PainteraBaseView paintera, final KeyTracker keyTracker)
	{
		final DelegateEventHandlers.AnyHandler filter = DelegateEventHandlers.handleAny();
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"enter shape interpolation mode",
						e -> enterMode(paintera, (ViewerPanelFX) e.getTarget()),
						e -> e.getTarget() instanceof ViewerPanelFX &&
							!isModeOn() &&
							!source.isApplyingMaskProperty().get() &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.S)
					)
			);
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"exit shape interpolation mode",
						e -> exitMode(paintera),
						e -> isModeOn() && keyTracker.areOnlyTheseKeysDown(KeyCode.ESCAPE)
					)
			);
		filter.addOnMousePressed(EventFX.MOUSE_PRESSED(
				"select object in current section",
				e -> selectObjectSection(paintera.sourceInfo(), e.getX(), e.getY()),
				e -> isModeOn() && e.isPrimaryButtonDown() && keyTracker.noKeysActive())
			);
		return filter;
	}

	public void enterMode(final PainteraBaseView paintera, final ViewerPanelFX viewer)
	{
		if (isModeOn())
		{
			LOG.info("Already in shape interpolation mode");
			return;
		}
		LOG.info("Entering shape interpolation mode");
		activeViewer.set(viewer);
		setDisableOtherViewers(true);

		lastAllowedActions = paintera.allowedActionsProperty().get();
		paintera.allowedActionsProperty().set(allowedActionsInShapeInterpolationMode);

		try
		{
			createMask();
			lastSelectedId = selectedIds.getLastSelection();
			lastActiveIds = selectedIds.getActiveIds();
			final long newLabelId = mask.info.value.get();
			converter.setColor(newLabelId, MASK_COLOR);
			selectedIds.activate(newLabelId);
		}
		catch (final MaskInUse e)
		{
			e.printStackTrace();
		}
	}

	public void exitMode(final PainteraBaseView paintera)
	{
		if (!isModeOn())
		{
			LOG.info("Not in shape interpolation mode");
			return;
		}
		LOG.info("Exiting shape interpolation mode");
		setDisableOtherViewers(false);

		paintera.allowedActionsProperty().set(lastAllowedActions);
		lastAllowedActions = null;

		final long newLabelId = mask.info.value.get();
		converter.removeColor(newLabelId);
		selectedIds.activate(lastActiveIds);
		selectedIds.activateAlso(lastSelectedId);
		lastSelectedId = Label.INVALID;
		lastActiveIds = null;
		forgetMask();
		activeViewer.get().requestRepaint();

		activeViewer.set(null);
	}

	public boolean isModeOn()
	{
		return activeViewer.get() != null;
	}

	private void createMask() throws MaskInUse
	{
		final ViewerState viewerState = activeViewer.get().getState();
		final int time = viewerState.timepointProperty().get();
		final int level = 0;
		final long labelId = idService.next();

		final AffineTransform3D labelTransform = new AffineTransform3D();
		source.getSourceTransform(time, level, labelTransform);
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewerState.getViewerTransform(viewerTransform);
		final AffineTransform3D labelToViewerTransform = viewerTransform.copy();
		labelToViewerTransform.concatenate(labelTransform);

		final MaskInfo<UnsignedLongType> maskInfo = new MaskInfo<>(time, level, new UnsignedLongType(labelId));
		mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
	}

	private void forgetMask()
	{
		mask = null;
		source.resetMasks();
	}

	private void setDisableOtherViewers(final boolean disable)
	{
		final Parent parent = activeViewer.get().getParent();
		for (final Node child : parent.getChildrenUnmodifiable())
		{
			if (child instanceof ViewerPanelFX && child != activeViewer.get())
			{
				final ViewerPanelFX viewer = (ViewerPanelFX) child;
				viewer.setDisable(disable);
				if (disable)
				{
					final ColorAdjust grayedOutEffect = new ColorAdjust();
					grayedOutEffect.setContrast(-0.2);
					grayedOutEffect.setBrightness(-0.5);
					viewer.setEffect(grayedOutEffect);
				}
				else
				{
					viewer.setEffect(null);
				}
			}
		}
	}

	private void selectObjectSection(final SourceInfo sourceInfo, final double x, final double y)
	{
		FloodFill2D.fillMaskAt(x, y, activeViewer.get(), mask, source, FILL_DEPTH);
		activeViewer.get().requestRepaint();
	}
}
