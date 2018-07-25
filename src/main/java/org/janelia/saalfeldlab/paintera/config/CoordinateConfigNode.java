package org.janelia.saalfeldlab.paintera.config;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.paintera.control.navigation.OrthogonalCrossSectionsIntersect;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoordinateConfigNode
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Label x = new Label();

	private final Label y = new Label();

	private final Label z = new Label();

	private final Label label = new Label("Center");

	private final Button setCenterButton = new Button("Set");

	private final GridPane contents = new GridPane();

	private final TransformListener<AffineTransform3D> listener = this::update;

	private final AffineTransform3D transform = new AffineTransform3D();

	private Consumer<AffineTransform3D> submitTransform = tf -> {
	};

	public CoordinateConfigNode()
	{
		super();

		contents.add(label, 0, 0);
		contents.add(x, 1, 0);
		contents.add(y, 2, 0);
		contents.add(z, 3, 0);
		contents.add(setCenterButton, 4, 0);
		contents.setHgap(5);

		GridPane.setHgrow(label, Priority.ALWAYS);

		x.setMaxWidth(50);
		y.setMaxWidth(50);
		z.setMaxWidth(50);
		setCenterButton.setMaxWidth(50);

		setCenterButton.setOnAction(event -> {

			final AffineTransform3D transform = new AffineTransform3D();
			final GridPane          gp        = new GridPane();
			final Dialog<double[]>  d         = new Dialog<>();
			final TextField         x         = new TextField();
			final TextField         y         = new TextField();
			final TextField         z         = new TextField();

			x.setPromptText("x");
			y.setPromptText("y");
			z.setPromptText("z");

			final Label lx = new Label("x");
			final Label ly = new Label("y");
			final Label lz = new Label("z");

			gp.add(lx, 0, 0);
			gp.add(ly, 0, 1);
			gp.add(lz, 0, 2);

			gp.add(x, 1, 0);
			gp.add(y, 1, 1);
			gp.add(z, 1, 2);

			d.getDialogPane().setContent(gp);
			d.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
			d.setResultConverter(bt -> {
				if (ButtonType.OK.equals(bt))
				{
					try
					{
						final double[] coordinate = new double[3];
						coordinate[0] = Double.parseDouble(x.getText());
						coordinate[1] = Double.parseDouble(y.getText());
						coordinate[2] = Double.parseDouble(z.getText());
						return coordinate;
					} catch (final Exception e)
					{
						return null;
					}
				}
				return null;
			});

			final double[] coordinate = d.showAndWait().orElse(null);

			if (coordinate != null)
			{
				final AffineTransform3D transformCopy = this.transform.copy();
				OrthogonalCrossSectionsIntersect.centerAt(transformCopy, coordinate[0], coordinate[1], coordinate[2]);
				submitTransform.accept(transformCopy);
			}

			event.consume();
		});

	}

	public void listen(final GlobalTransformManager manager)
	{
		manager.addListener(listener);
		final AffineTransform3D tf = new AffineTransform3D();
		manager.getTransform(tf);
		listener.transformChanged(tf);
		submitTransform = manager::setTransform;
	}

	public void hangup(final GlobalTransformManager manager)
	{
		manager.removeListener(listener);
		submitTransform = tf -> {
		};
	}

	public Node getContents()
	{
		return contents;
	}

	private void update(final AffineTransform3D tf)
	{
		this.transform.set(tf);
		// TODO update transform ui
		updateCenter(tf);
	}

	private void updateCenter(final AffineTransform3D tf)
	{
		LOG.debug("Updating center with transform {}", tf);
		final double[] center = new double[3];
		OrthogonalCrossSectionsIntersect.getCenter(tf, center);
		LOG.debug("New center = {}", center);
		// TODO do something better than rounding here
		x.setText(Long.toString(Math.round(center[0])));
		y.setText(Long.toString(Math.round(center[1])));
		z.setText(Long.toString(Math.round(center[2])));
	}

}
