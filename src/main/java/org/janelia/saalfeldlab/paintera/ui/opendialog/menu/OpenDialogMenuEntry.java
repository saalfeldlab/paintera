package org.janelia.saalfeldlab.paintera.ui.opendialog.menu;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.scijava.annotations.Indexable;
import org.scijava.plugin.SciJavaPlugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BiConsumer;

public interface OpenDialogMenuEntry extends SciJavaPlugin
{
	BiConsumer<PainteraBaseView, String> onAction();
}
