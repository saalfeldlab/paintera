package org.janelia.saalfeldlab.util.fx;

import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyProperty;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;

public class BindingUtils
{
	/**
	 * Helps to emulate binding between two properties where {@code changingProperty} may be modified on any thread,
	 * and {@code boundProperty} will be set on the FX thread.
	 *
	 * @param changingProperty
	 * @param boundProperty
	 * @param <T>
	 */
	public static <T> void bindCrossThread(final ReadOnlyProperty<T> changingProperty, final Property<? super T> boundProperty)
	{
		assert changingProperty != boundProperty;

		changingProperty.addListener((obs, old, newv) -> {
			InvokeOnJavaFXApplicationThread.invoke(() -> boundProperty.setValue(newv));
		});
	}
}
