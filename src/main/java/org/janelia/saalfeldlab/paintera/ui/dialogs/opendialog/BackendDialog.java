package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog;

import bdv.util.volatiles.SharedQueue;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.Group;
import javafx.scene.Node;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;

import java.util.concurrent.ExecutorService;

public interface BackendDialog {

  Node getDialogNode();

  ObservableValue<String> errorMessage();

  <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
  RawSourceState<T, V> getRaw(
		  final String name,
		  final SharedQueue queue,
		  final int priority) throws Exception;

  <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> LabelSourceState<D, T>
  getLabels(
		  final String name,
		  final SharedQueue queue,
		  final int priority,
		  final Group meshesGroup,
		  final ExecutorService manager,
		  final ExecutorService workers,
		  final String projectDirectory) throws Exception;

  DoubleProperty[] resolution();

  default void setResolution(final double[] resolution) {

	final DoubleProperty[] res = resolution();
	for (int i = 0; i < res.length; ++i) {
	  res[i].set(resolution[i]);
	}
  }

  DoubleProperty[] offset();

  default void setOffset(final double[] offset) {

	final DoubleProperty[] off = offset();
	for (int i = 0; i < off.length; ++i) {
	  off[i].set(offset[i]);
	}
  }

  DoubleProperty min();

  DoubleProperty max();

  ObservableStringValue nameProperty();

  String identifier();

  default Object metaData() {

	return null;
  }

}
