package org.janelia.saalfeldlab.paintera.ui.opendialog.menu;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.scijava.plugin.SciJavaPlugin;

import java.util.function.BiConsumer;

public interface OpenDialogMenuEntry extends SciJavaPlugin
{
	// string is project directory TODO: make project directory a Supplier<String>, or maybe a ReadOnlyStringProperty
	BiConsumer<PainteraBaseView, String> onAction();

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
