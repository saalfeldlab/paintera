package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers;
import org.janelia.saalfeldlab.paintera.serialization.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.EventHandler;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.WindowEvent;

public class SaveOnExitDialog implements EventHandler< WindowEvent >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final PainteraBaseView baseView;

	private final Properties properties;

	private final String project;

	private final Runnable onSuccess;

	public SaveOnExitDialog( final PainteraBaseView baseView, final Properties properties, final String project, final Runnable onSuccess )
	{
		super();
		this.baseView = baseView;
		this.properties = properties;
		this.project = project;
		this.onSuccess = onSuccess;
	}

	@Override
	public void handle( final WindowEvent event )
	{
		if ( properties.isDirty() )
		{
			final Dialog< ButtonType > d = new Dialog<>();
			d.setHeaderText( "Save before exit?" );
			final ButtonType saveButton = new ButtonType( "Yes", ButtonData.OK_DONE );
			final ButtonType discardButton = new ButtonType( "No", ButtonData.NO );
			final ButtonType cancelButton = new ButtonType( "Cancel", ButtonData.CANCEL_CLOSE );
			d.getDialogPane().getButtonTypes().setAll( saveButton, discardButton, cancelButton );
			final ButtonType response = d.showAndWait().orElse( ButtonType.CANCEL );

			if ( cancelButton.equals( response ) )
			{
				LOG.debug( "Canceling close request." );
				event.consume();
				return;
			}

			if ( saveButton.equals( response ) )
			{
				LOG.debug( "Saving project before exit" );
				if ( project == null )
				{
					LOG.error( "Unable to write project: Project directory not specified. Select NO in dialog to close." );
					event.consume();
					return;
				}
				try
				{
					SaveProject.persistProperties( project, properties, GsonHelpers.builderWithAllRequiredSerializers( baseView, this::project ).setPrettyPrinting() );
				}
				catch ( final IOException e )
				{
					LOG.error( "Unable to write project! Select NO in dialog to close." );
					LOG.error( "Exception: {}", e );
					event.consume();
					return;
				}
			}
			else if ( discardButton.equals( response ) )
			{
				LOG.debug( "Discarding project changes" );
			}

		}

		onSuccess.run();
	}

	private String project()
	{
		return this.project;
	}

}
