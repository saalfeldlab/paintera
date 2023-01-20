package org.janelia.saalfeldlab.paintera.ui;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;

public class CloseButton {

  public static FontAwesomeIconView createFontAwesome() {

	return createFontAwesome(1.0);
  }

  public static FontAwesomeIconView createFontAwesome(final double scale) {

	return FontAwesome.withIcon(FontAwesomeIcon.CLOSE, scale);
  }

}
