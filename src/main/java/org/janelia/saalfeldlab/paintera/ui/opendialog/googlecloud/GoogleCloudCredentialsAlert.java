package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class GoogleCloudCredentialsAlert
{

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String googleCloudSdkLink = "https://cloud.google.com/sdk/docs";

	private static final String googleCloudAuthCmd = "gcloud auth application-default login";

	public void show()
	{
		final Hyperlink hyperlink = new Hyperlink("Google Cloud SDK");
		hyperlink.setOnAction(e -> Paintera.getApplication().getHostServices().showDocument(googleCloudSdkLink));

		final TextArea area = new TextArea(googleCloudAuthCmd);
		area.setFont(Font.font("monospace"));
		area.setEditable(false);
		area.setMaxHeight(24);

		final TextFlow textFlow = new TextFlow(
				new Text("Please install "),
				hyperlink,
				new Text(" and then run this command to initialize the credentials:"),
				new Text(System.lineSeparator() + System.lineSeparator()),
				area);

		final Alert alert = PainteraAlerts.alert(Alert.AlertType.INFORMATION);
		alert.setHeaderText("Could not find Google Cloud credentials.");
		alert.getDialogPane().contentProperty().set(textFlow);
		alert.show();
	}
}
