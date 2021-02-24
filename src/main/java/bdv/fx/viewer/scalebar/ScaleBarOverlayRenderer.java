package bdv.fx.viewer.scalebar;

import bdv.fx.viewer.render.OverlayRendererGeneric;
import javafx.geometry.Bounds;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.Text;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import net.imglib2.util.LinAlgHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.measure.Unit;
import javax.measure.quantity.Length;
import java.lang.invoke.MethodHandles;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

public class ScaleBarOverlayRenderer implements OverlayRendererGeneric<GraphicsContext>, TransformListener<AffineTransform3D> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final List<Unit<Length>> UNITS = ScaleBarOverlayConfig.units();

  private final ScaleBarOverlayConfig config;

  //	private final DecimalFormat format = new DecimalFormat("0.####");

  /**
   * For finding the value to display on the scalebar: into how many parts is
   * each power of ten divided? For example, 4 means the following are
   * possible values:
   * <em>..., 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10, ...</em>
   */
  private final int subdivPerPowerOfTen = 4;

  private final AffineTransform3D transform = new AffineTransform3D();

  public ScaleBarOverlayRenderer(ScaleBarOverlayConfig config) {

	this.config = config;
  }

  private double width = 0.0;

  private double height = 0.0;

  @Override
  public synchronized void drawOverlays(GraphicsContext graphicsContext) {

	if (width > 0.0 && height > 0.0 && config.getIsShowing()) {
	  double targetLength = Math.min(width, config.getTargetScaleBarLength());
	  double[] onePixelWidth = {1.0, 0.0, 0.0};
	  transform.applyInverse(onePixelWidth, onePixelWidth);
	  final double globalCoordinateWidth = LinAlgHelpers.length(onePixelWidth);
	  // length of scale bar in global coordinate system
	  final double scaleBarWidth = targetLength * globalCoordinateWidth;
	  final double pot = Math.floor(Math.log10(scaleBarWidth));
	  final double l2 = scaleBarWidth / Math.pow(10, pot);
	  final int fracs = (int)(0.1 * l2 * subdivPerPowerOfTen);
	  final double scale1 = (fracs > 0) ? Math.pow(10, pot + 1) * fracs / subdivPerPowerOfTen : Math.pow(10, pot);
	  final double scale2 = (fracs == 3) ? Math.pow(10, pot + 1) : Math.pow(10, pot + 1) * (fracs + 1) / subdivPerPowerOfTen;

	  final double lB1 = scale1 / globalCoordinateWidth;
	  final double lB2 = scale2 / globalCoordinateWidth;

	  final double scale;
	  final double scaleBarLength;
	  if (Math.abs(lB1 - targetLength) < Math.abs(lB2 - targetLength)) {
		scale = scale1;
		scaleBarLength = lB1;
	  } else {
		scale = scale2;
		scaleBarLength = lB2;
	  }

	  final double[] ratios = UNITS.stream().mapToDouble(unit -> config.getBaseUnit().getConverterTo(unit).convert(scale)).toArray();
	  int firstSmallerThanZeroIndex = 0;
	  for (; firstSmallerThanZeroIndex < ratios.length; ++firstSmallerThanZeroIndex) {
		if (ratios[firstSmallerThanZeroIndex] < 1.0)
		  break;
	  }
	  final int unitIndex = Math.max(firstSmallerThanZeroIndex - 1, 0);
	  final Unit<Length> targetUnit = UNITS.get(unitIndex);
	  final double targetScale = config.getBaseUnit().getConverterTo(targetUnit).convert(scale);

	  final double x = 20;
	  final double y = height - 30;

	  final DecimalFormat format = new DecimalFormat(String.format("0.%s", String.join("", Collections.nCopies(config.getNumDecimals(), "#"))));
	  final String scaleBarText = format.format(targetScale) + targetUnit.toString();
	  final Text text = new Text(scaleBarText);
	  text.setFont(config.getOverlayFont());
	  final Bounds bounds = text.getBoundsInLocal();
	  final double tx = 20 + (scaleBarLength - bounds.getMaxX()) / 2;
	  final double ty = y - 5;
	  graphicsContext.setFill(config.getBackgroundColor());

	  // draw background
	  graphicsContext.fillRect(
			  x - 7,
			  ty - bounds.getHeight() - 3,
			  scaleBarLength + 14,
			  bounds.getHeight() + 25);

	  // draw scalebar
	  graphicsContext.setFill(config.getForegroundColor());
	  graphicsContext.fillRect(x, y, (int)scaleBarLength, 10);

	  // draw label
	  graphicsContext.setFont(config.getOverlayFont());
	  graphicsContext.fillText(scaleBarText, tx, ty);
	}
  }

  @Override
  public void setCanvasSize(int width, int height) {

	this.width = width;
	this.height = height;
  }

  @Override
  public void transformChanged(AffineTransform3D transform) {

	LOG.trace("Updating transform with {}, ignoring translation ", transform);
	this.transform.set(
			transform.get(0, 0), transform.get(0, 1), transform.get(0, 2), 0.0,
			transform.get(1, 0), transform.get(1, 1), transform.get(1, 2), 0.0,
			transform.get(2, 0), transform.get(2, 1), transform.get(2, 2), 0.0
	);

  }
}
