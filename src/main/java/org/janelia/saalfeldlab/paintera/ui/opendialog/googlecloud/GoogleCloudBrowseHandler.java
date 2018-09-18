package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.sun.javafx.application.PlatformImpl;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import javafx.util.Callback;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudClientSecretsPrompt;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("restriction")
public class GoogleCloudBrowseHandler
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String CLIENT_ID_KEY = "client_id";

	public static final String CLIENT_SECRET_KEY = "client_secret";

	public static final String INSTALLED_KEY = "installed";

	private GoogleCloudOAuth oauth;

	private final ObservableList<Project> projects = FXCollections.observableArrayList();

	private final ObservableList<Bucket> buckets = FXCollections.observableArrayList();

	public Optional<Pair<Storage, Bucket>> select(final Scene scene)
	{
		return select(scene.getWindow());
	}

	public Optional<Pair<Storage, Bucket>> select(final Window window)
	{
		try
		{
			oauth = GoogleCloudClientBuilder.createOAuth(googleCloudClientSecretsFromFileDialog(window));
		} catch (final Exception e)
		{
			//			ExceptionNode.exceptionDialog( e ).show();
			e.printStackTrace();
			return Optional.empty();
		}

		// query a list of user's projects first
		final ResourceManager resourceManager = GoogleCloudClientBuilder.createResourceManager(oauth);
		projects.clear();
		final Iterator<Project> projectIterator = resourceManager.list().iterateAll().iterator();
		if (!projectIterator.hasNext())
		{
			LOG.debug("No Google Cloud projects found for {}.", oauth);
			return null;
		}
		while (projectIterator.hasNext())
		{
			projects.add(projectIterator.next());
		}

		// add project names as list items

		final Dialog<Bucket> dialog = new Dialog<>();
		dialog.setTitle("Google Cloud");
		final GridPane contents = new GridPane();

		final ComboBox<Project> projectChoices = new ComboBox<>(projects);
		projectChoices.setPromptText("Project");
		final Callback<ListView<Project>, ListCell<Project>> projectCellFactory = lv -> new ListCell<Project>()
		{
			@Override
			protected void updateItem(final Project item, final boolean empty)
			{
				super.updateItem(item, empty);
				setText(empty ? "" : item.getName());
			}
		};
		projectChoices.setCellFactory(projectCellFactory);

		final ComboBox<Bucket> bucketChoices = new ComboBox<>(buckets);
		bucketChoices.setPromptText("Bucket");
		final Callback<ListView<Bucket>, ListCell<Bucket>> bucketCellFactory = lv -> new ListCell<Bucket>()
		{
			@Override
			protected void updateItem(final Bucket item, final boolean empty)
			{
				super.updateItem(item, empty);
				setText(empty ? "" : item.getName());
			}
		};
		bucketChoices.setCellFactory(bucketCellFactory);

		contents.add(projectChoices, 1, 0);
		contents.add(bucketChoices, 1, 1);

		dialog.getDialogPane().setContent(contents);

		dialog.setResultConverter(bt -> {
			if (ButtonType.OK.equals(bt)) { return bucketChoices.getValue(); }
			return null;
		});

		dialog.setResizable(true);

		bucketChoices.prefWidthProperty().bindBidirectional(projectChoices.prefWidthProperty());

		final ObjectBinding<Storage> storage = Bindings.createObjectBinding(
				() -> Optional
						.ofNullable(projectChoices.valueProperty().get())
						.map(s -> GoogleCloudClientBuilder.createStorage(oauth, s.getProjectId()))
						.orElse(null),
				projectChoices.valueProperty()
		                                                                   );

		storage.addListener((obs, oldv, newv) -> {
			if (newv == null || newv.equals(oldv)) { return; }
			final List<Bucket> buckets = new ArrayList<>();
			newv.list().iterateAll().forEach(buckets::add);
			this.buckets.setAll(buckets);
		});

		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		final Bucket selectedBucketName = dialog.showAndWait().orElse(null);

		if (selectedBucketName == null) { return Optional.empty(); }

		return Optional.of(new ValuePair<>(storage.get(), selectedBucketName));
		// String.format( "gs://%s/", selectedBucketName ) ) );
	}

	public static void main(final String[] args) throws IOException, InterruptedException
	{

		final GoogleCloudBrowseHandler handler = new GoogleCloudBrowseHandler();

		PlatformImpl.startup(() -> {
		});
		InvokeOnJavaFXApplicationThread.invoke(() -> {
			final Scene                 scene = new Scene(new StackPane(new Label("test")));
			final Pair<Storage, Bucket> sab   = handler.select(scene).orElse(new ValuePair<>(null, null));
			LOG.debug("storage={} bucket={}", sab.getA(), sab.getB());
		});

	}

	public static Supplier<GoogleClientSecrets> googleCloudClientSecretsFromFileDialog(final Window window)
	{
		final Dialog<GoogleClientSecrets> dialog = new Dialog<>();

		dialog.setTitle("Google Cloud Client Secrets");
		dialog.setResizable(true);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setHeaderText("Please provide your google cloud storage credentials.");
		// Credentials will be written into " +
		// GoogleCloudClientBuilder.CLIENT_SECRETS_FILE );

		final GridPane grid = new GridPane();

		final Label idLabel     = new Label("Client ID");
		final Label secretLabel = new Label("Client secret");

		final TextField idField     = new TextField();
		final TextField secretField = new TextField();

		grid.add(idLabel, 0, 0);
		grid.add(idField, 1, 0);
		grid.add(secretLabel, 0, 1);
		grid.add(secretField, 1, 1);

		GridPane.setHgrow(idField, Priority.ALWAYS);
		GridPane.setHgrow(secretField, Priority.ALWAYS);

		final TextField tf = new TextField();
		tf.setPromptText("Populate from file");
		final Button browseButton = new Button("Browse");
		browseButton.setTooltip(new Tooltip("Populate from file"));
		tf.setEditable(false);

		grid.add(browseButton, 0, 3);
		grid.add(tf, 1, 3);
		grid.setHgap(10);

		GridPane.setHgrow(tf, Priority.ALWAYS);

		dialog.getDialogPane().setContent(grid);

		final FileChooser chooser = new FileChooser();
		chooser.getExtensionFilters().addAll(new ExtensionFilter("json files", "*.json"));

		final Node           OKButton      = dialog.getDialogPane().lookupButton(ButtonType.OK);
		final BooleanBinding isValidId     = idField.textProperty().isNotEmpty();
		final BooleanBinding isValidSecret = secretField.textProperty().isNotEmpty();
		final BooleanBinding isValid       = isValidId.and(isValidSecret);
		isValid.addListener((obs, oldv, newv) -> OKButton.setDisable(!newv));

		dialog.setWidth(300);

		dialog.setResultConverter((bt) -> {
			if (ButtonType.CANCEL.equals(bt)) { return null; }
			final String id     = idField.getText();
			final String secret = secretField.getText();

			final JsonObject root     = new JsonObject();
			final JsonObject contents = new JsonObject();
			contents.addProperty(CLIENT_ID_KEY, id);
			contents.addProperty(CLIENT_SECRET_KEY, secret);
			root.add(INSTALLED_KEY, contents);

			LOG.debug("Returning json: {}", root);

			return GoogleCloudClientSecretsPrompt.create(
					contents.get(CLIENT_ID_KEY).getAsString(),
					contents.get(CLIENT_SECRET_KEY).getAsString()
			                                            );

		});

		browseButton.setOnAction(event -> {
			final File currentSelection = new File(Optional.ofNullable(tf.getText()).orElse(""));
			chooser.setInitialDirectory(currentSelection.isFile()
			                            ? currentSelection.getParentFile()
			                            : new File(System.getProperty("user.home")));
			final File selection = chooser.showOpenDialog(window);
			if (selection != null)
			{
				final String absPath = selection.getAbsolutePath();
				tf.setText(absPath);
				try (FileReader freader = new FileReader(selection))
				{
					final JsonReader reader = new JsonReader(freader);
					final JsonObject root   = new Gson().fromJson(reader, JsonObject.class);
					LOG.debug("Read json: {}", root);
					final JsonObject contents = root.get(INSTALLED_KEY).getAsJsonObject();
					idField.setText(contents.get(CLIENT_ID_KEY).getAsString());
					secretField.setText(contents.get(CLIENT_SECRET_KEY).getAsString());
				} catch (final IOException e)
				{
					LOG.debug("Unable to read json file at {} -- not auto-populating client id or secret.");
				}
			}
		});

		return () -> dialog.showAndWait().orElse(null);
	}

}
