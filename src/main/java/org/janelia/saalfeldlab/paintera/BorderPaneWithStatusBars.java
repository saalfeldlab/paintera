package org.janelia.saalfeldlab.paintera;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Source;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import net.imglib2.RealPoint;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms;
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.config.*;
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener;
import org.janelia.saalfeldlab.paintera.ui.Crosshair;
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs2;
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX;
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSlicesManager;
import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class BorderPaneWithStatusBars
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final BorderPane pane;

	private final PainteraGateway gateway = new PainteraGateway();

	private final HBox statusBar;

	private final ScrollPane sideBar;

	private final Label currentSourceStatus;

	private final Label viewerCoordinateStatus;

	private final Label worldCoordinateStatus;

	private final Label valueStatus;

	private final ResizeOnLeftSide resizeSideBar;

	private final NavigationConfigNode navigationConfigNode = new NavigationConfigNode();

	private final CrosshairConfigNode crosshairConfigNode = new CrosshairConfigNode();

	private final OrthoSliceConfigNode orthoSliceConfigNode = new OrthoSliceConfigNode();

	private final Viewer3DConfigNode viewer3DConfigNode = new Viewer3DConfigNode();

	private final ScreenScalesConfigNode screenScaleConfigNode = new ScreenScalesConfigNode();

	private final ScaleBarOverlayConfigNode scaleBarConfigNode = new ScaleBarOverlayConfigNode();

	private final BookmarkConfigNode bookmarkConfigNode;

	private final ArbitraryMeshConfigNode arbitraryMeshConfigNode = new ArbitraryMeshConfigNode(gateway.triangleMeshFormat());

	private final Map<ViewerAndTransforms, Crosshair> crossHairs;

	private final OrthoSlicesManager orthoSlicesManager;

	private final ObservableObjectValue<ViewerAndTransforms> currentFocusHolderWithState;

	private final Button saveProjectButton;

	public final ObservableObjectValue<ViewerAndTransforms> currentFocusHolder()
	{
		return this.currentFocusHolderWithState;
	}

	public BorderPane getPane()
	{
		return this.pane;
	}

	public void setViewerCoordinateStatus(final RealPoint p)
	{
		InvokeOnJavaFXApplicationThread.invoke(() -> viewerCoordinateStatus.setText(p == null
		                                                                            ? "N/A"
		                                                                            : String.format("(% 4d, % 4d)",
				                                                                            (int) p.getDoublePosition
						                                                                            (0),
				                                                                            (int) p
						                                                                            .getDoublePosition(1)
		                                                                                           )));
	}

	public void setWorldCoorinateStatus(final RealPoint p)
	{
		InvokeOnJavaFXApplicationThread.invoke(() -> worldCoordinateStatus.setText(p == null
		                                                                           ? "N/A"
		                                                                           : CoordinateDisplayListener
				                                                                           .worldToString(
				                                                                           p)));
	}

	public void setCurrentValue(final String s)
	{
		InvokeOnJavaFXApplicationThread.invoke(() -> valueStatus.setText(s));
	}

	public Map<ViewerAndTransforms, OrthoSliceFX> orthoSlices()
	{
		return this.orthoSlicesManager.getOrthoSlices();
	}

	public BorderPaneWithStatusBars(
			final PainteraBaseView center,
			final Supplier<String> project)
	{
		LOG.debug("Construction {}", BorderPaneWithStatusBars.class.getName());
		this.pane = new BorderPane(center.orthogonalViews().pane());

		this.currentFocusHolderWithState = currentFocusHolder(center.orthogonalViews());

		this.currentSourceStatus = new Label();
		this.viewerCoordinateStatus = new Label();
		this.worldCoordinateStatus = new Label();
		this.valueStatus = new Label();
		final CheckBox showStatusBar = new CheckBox();
		showStatusBar.setFocusTraversable(false);
		showStatusBar.setTooltip(new Tooltip("If not selected, status bar will only show on mouse-over"));

		this.bookmarkConfigNode =  new BookmarkConfigNode(bm -> {
			center.manager().setTransform(bm.getGlobalTransformCopy());
			center.viewer3D().setAffine(bm.getViewer3DTransformCopy());
		});

		this.crossHairs = makeCrosshairs(center.orthogonalViews(), Colors.CREMI, Color.WHITE.deriveColor(0, 1, 1,
				0.5));

		this.orthoSlicesManager = new OrthoSlicesManager(
				center.viewer3D().sceneGroup(),
				center.orthogonalViews(),
				center.manager(),
				center.viewer3D().eyeToWorldTransformProperty());

		final StackPane sourceDisplayStatus = new StackPane();

		// show source name by default, or override it with source status text if any
		center.sourceInfo().currentState().addListener((obs, oldv, newv) -> {
			if (newv == null || newv.getDisplayStatus() == null)
				sourceDisplayStatus.getChildren().clear();
			else
				sourceDisplayStatus.getChildren().setAll(newv.getDisplayStatus());
			currentSourceStatus.textProperty().unbind();
			currentSourceStatus.textProperty().bind(Bindings.createStringBinding(
					() -> {
						if (newv.statusTextProperty() != null && newv.statusTextProperty().get() != null && !newv.statusTextProperty().get().isEmpty())
							return newv.statusTextProperty().get();
						else if (newv.nameProperty().get() != null)
							return newv.nameProperty().get();
						else
							return null;
					},
					newv.nameProperty(),
					newv.statusTextProperty()
				));
		});

		// for positioning the 'show status bar' checkbox on the right
		final Region valueStatusSpacing = new Region();
		HBox.setHgrow(valueStatusSpacing, Priority.ALWAYS);

		this.statusBar = new HBox(5,
				sourceDisplayStatus,
				currentSourceStatus,
				viewerCoordinateStatus,
				worldCoordinateStatus,
				valueStatus,
				valueStatusSpacing,
				showStatusBar
		);

		final Tooltip currentSourceStatusToolTip = new Tooltip();
		currentSourceStatusToolTip.textProperty().bind(currentSourceStatus.textProperty());
		currentSourceStatus.setTooltip(currentSourceStatusToolTip);

		currentSourceStatus.setPrefWidth(95.0);
		viewerCoordinateStatus.setPrefWidth(115.0);
		worldCoordinateStatus.setPrefWidth(245.0);

		viewerCoordinateStatus.setFont(Font.font("Monospaced"));
		worldCoordinateStatus.setFont(Font.font("Monospaced"));

		final BooleanProperty isWithinMarginOfBorder = new SimpleBooleanProperty();
		pane.addEventFilter(
				MouseEvent.MOUSE_MOVED,
				e -> isWithinMarginOfBorder.set(e.getY() < pane.getHeight() && pane.getHeight() - e.getY() <=
						statusBar.getHeight())
		                   );
		statusBar.visibleProperty().addListener((obs, oldv, newv) -> pane.setBottom(newv ? statusBar : null));
		statusBar.visibleProperty().bind(isWithinMarginOfBorder.or(showStatusBar.selectedProperty()));
		showStatusBar.setSelected(true);

		final BiConsumer<Source<?>, Exception> onRemoveException = (s, e) -> {
			LOG.warn("Unable to remove source: {}", e.getMessage());
		};

		final SourceTabs2 sourceTabs = new SourceTabs2(center.sourceInfo());
		final TitledPane sourcesContents = new TitledPane("sources", sourceTabs.getNode());
		sourcesContents.setExpanded(false);

		final VBox settingsContents = new VBox(
				this.navigationConfigNode.getContents(),
				this.crosshairConfigNode.getContents(),
				this.orthoSliceConfigNode.getContents(),
				this.viewer3DConfigNode.getContents(),
				this.scaleBarConfigNode,
				this.bookmarkConfigNode,
				this.arbitraryMeshConfigNode,
				this.screenScaleConfigNode.getContents());
		final TitledPane settings = new TitledPane("settings", settingsContents);
		settings.setExpanded(false);

		center.viewer3D().meshesGroup().getChildren().add(this.arbitraryMeshConfigNode.getMeshGroup());

		saveProjectButton = new Button("Save");

		final GridPane saveProjectButtonPane = new GridPane();
		saveProjectButtonPane.add(saveProjectButton, 0, 0);
		GridPane.setMargin(saveProjectButton, new Insets(7.0));

		this.sideBar = new ScrollPane(new VBox(sourcesContents, settings, saveProjectButtonPane));
		this.sideBar.setHbarPolicy(ScrollBarPolicy.NEVER);
		this.sideBar.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
		this.sideBar.setVisible(true);
		this.sideBar.prefWidthProperty().set(280);
		sourceTabs.widthProperty().bind(sideBar.prefWidthProperty());
		settingsContents.prefWidthProperty().bind(sideBar.prefWidthProperty());

		resizeSideBar = new ResizeOnLeftSide(sideBar, sideBar.prefWidthProperty());
	}

	public ScrollPane getSideBar()
	{
		return sideBar;
	}

	public void toggleSideBar()
	{
		if (pane.getRight() == null)
		{
			pane.setRight(sideBar);
			resizeSideBar.install();
		}
		else
		{
			resizeSideBar.remove();
			pane.setRight(null);
		}
	}

	public ObjectProperty<EventHandler<ActionEvent>> saveProjectButtonOnActionProperty()
	{
		return this.saveProjectButton.onActionProperty();
	}

	public static Map<ViewerAndTransforms, Crosshair> makeCrosshairs(
			final OrthogonalViews<?> views,
			final Color onFocusColor,
			final Color offFocusColor)
	{
		final Map<ViewerAndTransforms, Crosshair> map = new HashMap<>();
		map.put(views.topLeft(), makeCrossHairForViewer(views.topLeft().viewer(), onFocusColor, offFocusColor));
		map.put(views.topRight(), makeCrossHairForViewer(views.topRight().viewer(), onFocusColor, offFocusColor));
		map.put(views.bottomLeft(), makeCrossHairForViewer(views.bottomLeft().viewer(), onFocusColor, offFocusColor));
		return map;
	}

	public static Crosshair makeCrossHairForViewer(
			final ViewerPanelFX viewer,
			final Color onFocusColor,
			final Color offFocusColor)
	{
		final Crosshair ch = new Crosshair();
		viewer.getDisplay().addOverlayRenderer(ch);
		ch.wasChangedProperty().addListener((obs, oldv, newv) -> viewer.getDisplay().drawOverlays());
		ch.isHighlightProperty().bind(viewer.focusedProperty());
		return ch;
	}

	public static ObservableObjectValue<ViewerAndTransforms> currentFocusHolder(final OrthogonalViews<?> views)
	{
		final ViewerAndTransforms     tl      = views.topLeft();
		final ViewerAndTransforms     tr      = views.topRight();
		final ViewerAndTransforms     bl      = views.bottomLeft();
		final ReadOnlyBooleanProperty focusTL = tl.viewer().focusedProperty();
		final ReadOnlyBooleanProperty focusTR = tr.viewer().focusedProperty();
		final ReadOnlyBooleanProperty focusBL = bl.viewer().focusedProperty();

		return Bindings.createObjectBinding(
				() -> {
					return focusTL.get() ? tl : focusTR.get() ? tr : focusBL.get() ? bl : null;
				},
				focusTL,
				focusTR,
				focusBL
		                                   );

	}

	public NavigationConfigNode navigationConfigNode()
	{
		return this.navigationConfigNode;
	}

	public CrosshairConfigNode crosshairConfigNode()
	{
		return this.crosshairConfigNode;
	}

	public OrthoSliceConfigNode orthoSliceConfigNode()
	{
		return this.orthoSliceConfigNode;
	}

	public ScreenScalesConfigNode screenScalesConfigNode() { return this.screenScaleConfigNode; }

	public Viewer3DConfigNode viewer3DConfigNode()
	{
		return this.viewer3DConfigNode;
	}

	public ScaleBarOverlayConfigNode scaleBarOverlayConfigNode() {
		return this.scaleBarConfigNode;
	}

	public BookmarkConfigNode bookmarkConfigNode() {
		return this.bookmarkConfigNode;
	}

	public ArbitraryMeshConfigNode arbitraryMeshConfigNode() {
		return this.arbitraryMeshConfigNode;
	}

	public Map<ViewerAndTransforms, Crosshair> crosshairs()
	{
		return Collections.unmodifiableMap(crossHairs);
	}
}
