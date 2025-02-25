package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.scijava.plugin.SciJavaPlugin;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public interface OpenDialogMenuEntry extends SciJavaPlugin {

	// Supplier<String> provides the project directory
	BiConsumer<PainteraBaseView, Supplier<String>> onAction();

	// add arbitrary entry to open dialog like this:
	//	@Plugin(type = OpenDialogMenuEntry.class, menuPath = "_A>_B>_C", priority = Double.MAX_VALUE)
	//	public static final class Dummy implements OpenDialogMenuEntry {
	//
	//		@Override
	//		public BiConsumer<PainteraBaseView, String> onAction() {
	//			return (pbv, string) -> {System.out.println(string + " Dummy entry!");};
	//		}
	//	}

}
