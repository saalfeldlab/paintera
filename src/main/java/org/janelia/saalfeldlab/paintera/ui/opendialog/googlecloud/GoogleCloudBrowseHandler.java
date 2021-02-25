package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.pivovarit.function.ThrowingFunction;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import javafx.util.Callback;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("restriction")
public class GoogleCloudBrowseHandler {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ObservableList<Project> projects = FXCollections.observableArrayList();

  private final ObservableList<Bucket> buckets = FXCollections.observableArrayList();

  public Optional<Pair<Storage, Bucket>> select(final Scene scene) {

	return select(scene.getWindow());
  }

  public Optional<Pair<Storage, Bucket>> select(final Window window) {

	final ResourceManager resourceManager;
	try {
	  resourceManager = GoogleCloudClientBuilder.createResourceManager();
	} catch (final Exception e) {
	  new GoogleCloudCredentialsAlert().show();
	  return Optional.empty();
	}

	// query a list of user's projects first
	projects.clear();
	final Iterator<Project> projectIterator = resourceManager.list().iterateAll().iterator();
	if (!projectIterator.hasNext()) {
	  LOG.debug("No Google Cloud projects were found.");
	  return Optional.empty();
	}
	while (projectIterator.hasNext()) {
	  projects.add(projectIterator.next());
	}

	// add project names as list items

	final Dialog<Bucket> dialog = new Dialog<>();
	dialog.setTitle("Google Cloud");
	final GridPane contents = new GridPane();

	final ComboBox<Project> projectChoices = new ComboBox<>(projects);
	projectChoices.setPromptText("Project");
	final Callback<ListView<Project>, ListCell<Project>> projectCellFactory = lv -> new ListCell<Project>() {

	  @Override
	  protected void updateItem(final Project item, final boolean empty) {

		super.updateItem(item, empty);
		setText(empty ? "" : item.getName());
	  }
	};
	projectChoices.setCellFactory(projectCellFactory);

	final ComboBox<Bucket> bucketChoices = new ComboBox<>(buckets);
	bucketChoices.setPromptText("Bucket");
	final Callback<ListView<Bucket>, ListCell<Bucket>> bucketCellFactory = lv -> new ListCell<Bucket>() {

	  @Override
	  protected void updateItem(final Bucket item, final boolean empty) {

		super.updateItem(item, empty);
		setText(empty ? "" : item.getName());
	  }
	};
	bucketChoices.setCellFactory(bucketCellFactory);

	contents.add(projectChoices, 1, 0);
	contents.add(bucketChoices, 1, 1);

	dialog.getDialogPane().setContent(contents);

	dialog.setResultConverter(bt -> {
	  if (ButtonType.OK.equals(bt)) {
		return bucketChoices.getValue();
	  }
	  return null;
	});

	dialog.setResizable(true);

	bucketChoices.prefWidthProperty().bindBidirectional(projectChoices.prefWidthProperty());

	final ObjectBinding<Storage> storage = Bindings.createObjectBinding(
			() -> Optional
					.ofNullable(projectChoices.valueProperty().get())
					.map(ThrowingFunction.unchecked(s -> GoogleCloudClientBuilder.createStorage(s.getProjectId())))
					.orElse(null),
			projectChoices.valueProperty());

	storage.addListener((obs, oldv, newv) -> {
	  if (newv == null || newv.equals(oldv)) {
		return;
	  }
	  final List<Bucket> buckets = new ArrayList<>();
	  newv.list().iterateAll().forEach(buckets::add);
	  this.buckets.setAll(buckets);
	});

	dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
	final Bucket selectedBucketName = dialog.showAndWait().orElse(null);

	if (selectedBucketName == null)
	  return Optional.empty();

	return Optional.of(new ValuePair<>(storage.get(), selectedBucketName));
	// String.format( "gs://%s/", selectedBucketName ) ) );
  }
}
