package org.janelia.saalfeldlab.paintera.control;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Source;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.InstallAndRemove;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.control.paint.Fill2DOverlay;
import org.janelia.saalfeldlab.paintera.control.paint.FillOverlay;
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill;
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D;
import org.janelia.saalfeldlab.paintera.control.paint.PaintActions2D;
import org.janelia.saalfeldlab.paintera.control.paint.RestrictPainting;
import org.janelia.saalfeldlab.paintera.control.paint.SelectNextId;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Paint implements ToOnEnterOnExit
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final HashMap<ViewerPanelFX, Collection<InstallAndRemove<Node>>> mouseAndKeyHandlers = new HashMap<>();

	private final HashMap<ViewerPanelFX, PaintActions2D> painters = new HashMap<>();

	private final SourceInfo sourceInfo;

	private final KeyTracker keyTracker;

	private final GlobalTransformManager manager;

	private final SimpleDoubleProperty brushRadius = new SimpleDoubleProperty(5.0);

	private final SimpleDoubleProperty brushRadiusIncrement = new SimpleDoubleProperty(1.0);

	private final SimpleDoubleProperty brushDepth = new SimpleDoubleProperty(1.0);

	private final Runnable requestRepaint;

	private final BooleanProperty paint3D = new SimpleBooleanProperty(false);

	private final BooleanBinding paint2D = paint3D.not();

	private final ExecutorService paintQueue;

	public Paint(
			final SourceInfo sourceInfo,
			final KeyTracker keyTracker,
			final GlobalTransformManager manager,
			final Runnable requestRepaint,
			final ExecutorService paintQueue)
	{
		super();
		this.sourceInfo = sourceInfo;
		this.keyTracker = keyTracker;
		this.manager = manager;
		this.requestRepaint = requestRepaint;
		this.paintQueue = paintQueue;
	}

	@Override
	public Consumer<ViewerPanelFX> getOnEnter()
	{
		return t -> {
			//			if ( this.paintableViews.contains( this.viewerAxes.get( t ) ) )
			{
				if (!this.mouseAndKeyHandlers.containsKey(t))
				{
					final PaintActions2D paint2D = new PaintActions2D(
							t,
							sourceInfo,
							manager,
							requestRepaint,
							paintQueue
					);
					paint2D.brushRadiusProperty().bindBidirectional(this.brushRadius);
					paint2D.brushRadiusIncrementProperty().bindBidirectional(this.brushRadiusIncrement);
					paint2D.brushDepthProperty().bindBidirectional(this.brushDepth);
					final ObjectProperty<Source<?>> currentSource = sourceInfo.currentSourceProperty();
					final ObjectBinding<SelectedIds> currentSelectedIds = Bindings.createObjectBinding(
							() -> selectedIdsFromState(sourceInfo.getState(currentSource.get())),
							currentSource
					                                                                                  );

					final Supplier<Long> paintSelection = () -> {
						final SelectedIds csi = currentSelectedIds.get();

						if (csi == null)
						{
							LOG.debug("Source {} does not provide selected ids.", currentSource.get());
							return null;
						}

						final long lastSelection = csi.getLastSelection();
						LOG.debug("Last selection is {}", lastSelection);
						return Label.regular(lastSelection) ? lastSelection : null;
					};

					painters.put(t, paint2D);

					final FloodFill   fill   = new FloodFill(t, sourceInfo, requestRepaint);
					final FloodFill2D fill2D = new FloodFill2D(t, sourceInfo, requestRepaint);
					fill2D.fillDepthProperty().bindBidirectional(this.brushDepth);
					final Fill2DOverlay fill2DOverlay = new Fill2DOverlay(t);
					fill2DOverlay.brushDepthProperty().bindBidirectional(this.brushDepth);
					final FillOverlay fillOverlay = new FillOverlay(t);

					final RestrictPainting restrictor = new RestrictPainting(t, sourceInfo, requestRepaint);

					final List<InstallAndRemove<Node>> iars = new ArrayList<>();

					// brush
					iars.add(EventFX.KEY_PRESSED(
							"show brush overlay",
							event -> paint2D.showBrushOverlay(),
							event -> keyTracker.areKeysDown(KeyCode.SPACE)
					                            ));
					iars.add(EventFX.KEY_RELEASED(
							"show brush overlay",
							event -> paint2D.hideBrushOverlay(),
							event -> event.getCode().equals(KeyCode.SPACE) && !keyTracker.areKeysDown(KeyCode.SPACE)
					                             ));
					iars.add(EventFX.SCROLL(
							"change brush size",
							event -> paint2D.changeBrushRadius(event.getDeltaY()),
							event -> keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE)
					                       ));
					iars.add(EventFX.SCROLL(
							"change brush depth",
							event -> paint2D.changeBrushDepth(event.getDeltaY()),
							event -> keyTracker.areOnlyTheseKeysDown(
									KeyCode.SPACE,
									KeyCode.SHIFT
							                                        ) || keyTracker.areOnlyTheseKeysDown(KeyCode.F) ||
									keyTracker.areOnlyTheseKeysDown(
									KeyCode.SHIFT,
									KeyCode.F
							                                                                                                                          )
					                       ));
					iars.add(EventFX.KEY_PRESSED("show fill 2D overlay", event -> {
						fill2DOverlay.setVisible(true);
						fillOverlay.setVisible(false);
					}, event -> keyTracker.areOnlyTheseKeysDown(KeyCode.F)));
					iars.add(EventFX.KEY_RELEASED(
							"show fill 2D overlay",
							event -> fill2DOverlay.setVisible(false),
							event -> event.getCode().equals(KeyCode.F) && keyTracker.noKeysActive()
					                             ));
					iars.add(EventFX.KEY_PRESSED("show fill overlay", event -> {
						fillOverlay.setVisible(true);
						fill2DOverlay.setVisible(false);
					}, event -> keyTracker.areOnlyTheseKeysDown(KeyCode.F, KeyCode.SHIFT)));
					iars.add(EventFX.KEY_RELEASED(
							"show fill overlay",
							event -> fillOverlay.setVisible(false),
							event -> event.getCode().equals(KeyCode.F) && keyTracker.areOnlyTheseKeysDown(KeyCode
									.SHIFT) || event.isShiftDown() && keyTracker.areOnlyTheseKeysDown(
									KeyCode.F)
					                             ));

					// click paint
					iars.add(paint2D.clickPaintLabel(
							"paint 2D",
							paintSelection::get,
							event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE) &&
									this.paint2D.get()
					                                ));
					iars.add(paint2D.clickPaintLabel(
							"erase canvas click 2D",
							() -> Label.TRANSPARENT,
							event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE)
									&& this.paint2D.get()
					                                ));
					iars.add(paint2D.clickPaintLabel(
							"to background 2D",
							() -> Label.BACKGROUND,
							event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(
									KeyCode.SPACE,
									KeyCode.SHIFT
							                                                                         ) && this.paint2D
									.get()
					                                ));

					// drag paint
					iars.add(paint2D.dragPaintLabel(
							"paint 2D",
							paintSelection::get,
							event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE) &&
									this.paint2D.get()
					                               ));
					iars.add(paint2D.dragPaintLabel(
							"erase canvas 2D",
							() -> Label.TRANSPARENT,
							event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE)
									&& this.paint2D.get()
					                               ));
					iars.add(paint2D.dragPaintLabel(
							"to background 2D",
							() -> Label.BACKGROUND,
							event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(
									KeyCode.SPACE,
									KeyCode.SHIFT
							                                                                         ) && this.paint2D
									.get()
					                               ));

					// advanced paint stuff
					iars.add(EventFX.MOUSE_PRESSED(
							"fill",
							event -> fill.fillAt(event.getX(), event.getY(), paintSelection::get),
							event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(
									KeyCode.SHIFT,
									KeyCode.F
							                                                                       )
					                              ));
					iars.add(EventFX.MOUSE_PRESSED(
							"fill 2D",
							event -> fill2D.fillAt(event.getX(), event.getY(), paintSelection::get),
							event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.F)
					                              ));
					iars.add(EventFX.MOUSE_PRESSED(
							"restrict",
							event -> restrictor.restrictTo(event.getX(), event.getY()),
							event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(
									KeyCode.SHIFT,
									KeyCode.R
							                                                                       )
					                              ));

					final SelectNextId nextId = new SelectNextId(sourceInfo);
					iars.add(EventFX.KEY_PRESSED(
							"next id",
							event -> nextId.getNextId(),
							event -> keyTracker.areOnlyTheseKeysDown(KeyCode.N)
					                            ));

					this.mouseAndKeyHandlers.put(t, iars);
				}
				this.mouseAndKeyHandlers.get(t).forEach(handler -> {
					handler.installInto(t);
				});
			}

		};
	}

	public BooleanProperty paint3DProperty()
	{
		return this.paint3D;
	}

	@Override
	public Consumer<ViewerPanelFX> getOnExit()
	{
		return t -> {
			if (this.mouseAndKeyHandlers.containsKey(t))
			{
				if (painters.containsKey(t))
				{
					painters.get(t).setBrushOverlayVisible(false);
				}
				this.mouseAndKeyHandlers.get(t).forEach(handler -> {
					handler.removeFrom(t);
				});
			}
		};
	}

	public SelectedIds selectedIdsFromState(final SourceState<?, ?> state)
	{
		return state instanceof LabelSourceState<?, ?>
		       ? ((LabelSourceState<?, ?>) state).selectedIds()
		       : null;
	}

}
