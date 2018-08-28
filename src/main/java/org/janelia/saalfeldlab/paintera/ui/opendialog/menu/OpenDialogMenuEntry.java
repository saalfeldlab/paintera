package org.janelia.saalfeldlab.paintera.ui.opendialog.menu;

import javafx.event.ActionEvent;
import net.imglib2.loops.LoopBuilder;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.scijava.annotations.Indexable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface OpenDialogMenuEntry {

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@Target(ElementType.TYPE)
	@Indexable
	@interface OpenDialogMenuEntryPath
	{
		String path();

		int rank() default Integer.MAX_VALUE;
	}

	LoopBuilder.TriConsumer<PainteraBaseView, ActionEvent, String> onAction();

}
