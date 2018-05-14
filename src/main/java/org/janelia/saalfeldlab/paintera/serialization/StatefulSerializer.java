package org.janelia.saalfeldlab.paintera.serialization;

import java.util.concurrent.ExecutorService;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

import bdv.util.volatiles.SharedQueue;
import javafx.scene.Group;

public class StatefulSerializer
{

	public static class Arguments
	{
		public final SharedQueue sharedQueue;

		public final ExecutorService generalPurposeExecutors;

		public final ExecutorService meshManagerExecutors;

		public final ExecutorService meshWorkersExecutors;

		public final ExecutorService propagationWorkers;

		public final Group meshesGroup;

		public Arguments( final PainteraBaseView viewer )
		{
			this.sharedQueue = viewer.getQueue();
			this.generalPurposeExecutors = viewer.generalPurposeExecutorService();
			this.meshManagerExecutors = viewer.getMeshManagerExecutorService();
			this.meshWorkersExecutors = viewer.getMeshWorkerExecutorService();
			this.propagationWorkers = viewer.getPropagationQueue();
			this.meshesGroup = viewer.viewer3D().meshesGroup();
		}
	}

	public static interface SerializerAndDeserializer< T, S extends JsonDeserializer< T > & JsonSerializer< T > >
	{
		public S create(
				Arguments arguments,
				Supplier< String > projectDirectory,
				IntFunction< SourceState< ?, ? > > dependencyFromIndex );
	}

}
