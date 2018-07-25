package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.googlecloud.CredentialProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialProviderWithWebView implements CredentialProvider
{

	private static final String USER_ID = "user";

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final VerificationCodeReceiver receiver;

	public CredentialProviderWithWebView()
	{
		this(new LocalServerReceiver());
	}

	public CredentialProviderWithWebView(final VerificationCodeReceiver receiver)
	{
		super();
		this.receiver = receiver;
	}

	@Override
	public Credential fromFlow(final AuthorizationCodeFlow flow) throws IOException
	{

		LOG.debug("Creating credential from flow {}", flow);
		try
		{
			final Credential credential = flow.loadCredential(USER_ID);
			if (credential != null
					&& (credential.getRefreshToken() != null ||
					credential.getExpiresInSeconds() == null ||
					credential.getExpiresInSeconds() > 60)) { return credential; }
			// open in browser
			final String redirectUri = receiver.getRedirectUri();
			final AuthorizationCodeRequestUrl authorizationUrl =
					flow.newAuthorizationUrl().setRedirectUri(redirectUri);
			browse(authorizationUrl.build());
			// receive authorization code and exchange it for an access token
			final String        code     = receiver.waitForCode();
			final TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
			// store credential and return it
			return flow.createAndStoreCredential(response, USER_ID);
		} finally
		{
			receiver.stop();
		}
	}

	public static void browse(final String url)
	{
		// Ask user to open in their browser using copy-paste
		System.out.println("Please open the following address in your browser:");
		System.out.println("  " + url);

		final WebView    webView         = new WebView();
		final BorderPane root            = new BorderPane(webView);
		final TextField  currentUrlField = new TextField(url);
		final Button     reloadButton    = new Button("reload");
		reloadButton.setOnAction(e -> webView.getEngine().load(url));
		root.setTop(new HBox(currentUrlField, reloadButton));
		HBox.setHgrow(currentUrlField, Priority.ALWAYS);
		webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldv, newv) -> {
			if (Worker.State.SUCCEEDED.equals(newv))
			{
				currentUrlField.setText(webView.getEngine().getLocation());
			}
		});
		webView.getEngine().load(url);

		InvokeOnJavaFXApplicationThread.invoke(() -> {
			final Stage stage = new Stage();
			final Scene scene = new Scene(root);
			stage.setScene(scene);
			stage.showAndWait();
		});

	}

}
