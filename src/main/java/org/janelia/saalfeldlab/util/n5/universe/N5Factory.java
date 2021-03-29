/**
 * Copyright (c) 2017-2021, Saalfeld lab, HHMI Janelia
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * <p>
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.util.n5.universe;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;
import com.google.gson.GsonBuilder;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageURI;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageReader;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageWriter;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

/**
 * Factory for various N5 readers and writers.  Implementation specific
 * parameters can be provided to the factory instance and will be used when
 * such implementations are generated and ignored otherwise. Reasonable
 * defaults are provided.
 *
 * @author Stephan Saalfeld
 * @author John Bogovic
 * @author Igor Pisarev
 */
public class N5Factory implements Serializable {

  private static final byte[] HDF5_SIG = {(byte)137, 72, 68, 70, 13, 10, 26, 10};
  private int[] hdf5DefaultBlockSize = {64, 64, 64, 1, 1};
  private boolean hdf5OverrideBlockSize = false;
  private GsonBuilder gsonBuilder = new GsonBuilder();
  private String zarrDimensionSeparator = ".";
  private boolean zarrMapN5DatasetAttributes = true;
  private String googleCloudProjectId = null;

  public N5Factory hdf5DefaultBlockSize(final int... blockSize) {

	hdf5DefaultBlockSize = blockSize;
	return this;
  }

  public N5Factory hdf5OverrideBlockSize(final boolean override) {

	hdf5OverrideBlockSize = override;
	return this;
  }

  public N5Factory gsonBuilder(final GsonBuilder gsonBuilder) {

	this.gsonBuilder = gsonBuilder;
	return this;
  }

  public N5Factory zarrDimensionSeparator(final String separator) {

	zarrDimensionSeparator = separator;
	return this;
  }

  public N5Factory zarrMapN5Attributes(final boolean mapAttributes) {

	zarrMapN5DatasetAttributes = mapAttributes;
	return this;
  }

  public N5Factory googleCloudProjectId(final String projectId) {

	googleCloudProjectId = projectId;
	return this;
  }

  private static boolean isHDF5(final String path) throws FileNotFoundException, IOException {

	if (Files.isRegularFile(Paths.get(path))) {
	  /* optimistic */
	  if (path.matches("(?i).*\\.(h5|hdf5)"))
		return true;
	  else {
		try (final FileInputStream in = new FileInputStream(new File(path))) {
		  final byte[] sig = new byte[8];
		  in.read(sig);
		  return Arrays.equals(sig, HDF5_SIG);
		}
	  }
	}
	return false;
  }

  /**
   * Helper method.
   *
   * @param url
   * @return
   */
  private static AmazonS3 createS3(final String url) {

	final AmazonS3 s3;
	AWSCredentials credentials = null;
	try {
	  credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
	} catch (final Exception e) {
	  System.out.println("Could not load AWS credentials, falling back to anonymous.");
	}
	final AWSStaticCredentialsProvider credentialsProvider =
			new AWSStaticCredentialsProvider(credentials == null ? new AnonymousAWSCredentials() : credentials);

	final AmazonS3URI uri = new AmazonS3URI(url);
	final Optional<String> region = Optional.ofNullable(uri.getRegion());

	if (region.isPresent()) {
	  s3 = AmazonS3ClientBuilder.standard()
			  .withCredentials(credentialsProvider)
			  .withRegion(region.map(Regions::fromName).orElse(Regions.US_EAST_1))
			  .build();
	} else {
	  s3 = AmazonS3ClientBuilder.standard()
			  .withCredentials(credentialsProvider)
			  .withRegion(Regions.US_EAST_1)
			  .build();
	}

	return s3;
  }

  /**
   * Open an {@link N5Reader} for N5 filesystem.
   *
   * @param path
   * @return
   * @throws IOException
   */
  public N5FSReader openFSReader(final String path) throws IOException {

	return new N5FSReader(path, gsonBuilder);
  }

  /**
   * Open an {@link N5Reader} for Zarr.
   *
   * For more options of the Zarr backend study the {@link N5ZarrReader}
   * constructors.
   *
   * @param path
   * @return
   * @throws IOException
   */
  public N5ZarrReader openZarrReader(final String path) throws IOException {

	return new N5ZarrReader(path, gsonBuilder, zarrDimensionSeparator, zarrMapN5DatasetAttributes);
  }

  /**
   * Open an {@link N5Reader} for HDF5. Close the reader when you do not need
   * it any more.
   *
   * For more options of the HDF5 backend study the {@link N5HDF5Reader}
   * constructors.
   *
   * @param path
   * @return
   * @throws IOException
   */
  public N5HDF5Reader openHDF5Reader(final String path) throws IOException {

	return new N5HDF5Reader(path, hdf5OverrideBlockSize, gsonBuilder, hdf5DefaultBlockSize);
  }

  /**
   * Open an {@link N5Reader} for Google Cloud.
   *
   * @param url
   * @return
   * @throws IOException
   */
  public N5GoogleCloudStorageReader openGoogleCloudReader(final String url) throws IOException {

	final GoogleCloudStorageClient storageClient = new GoogleCloudStorageClient();
	final Storage storage = storageClient.create();
	final GoogleCloudStorageURI googleCloudUri = new GoogleCloudStorageURI(url);

	return new N5GoogleCloudStorageReader(
			storage,
			googleCloudUri.getBucket(),
			googleCloudUri.getKey(),
			gsonBuilder);
  }

  /**
   * Open an {@link N5Reader} for AWS S3.
   *
   * @param url
   * @return
   * @throws IOException
   */
  public N5AmazonS3Reader openAWSS3Reader(final String url) throws IOException {

	return new N5AmazonS3Reader(
			createS3(url),
			new AmazonS3URI(url),
			gsonBuilder);
  }

  /**
   * Open an {@link N5Writer} for N5 filesystem.
   *
   * @param path
   * @return
   * @throws IOException
   */
  public N5FSWriter openFSWriter(final String path) throws IOException {

	return new N5FSWriter(path, gsonBuilder);
  }

  /**
   * Open an {@link N5Writer} for Zarr.
   *
   * For more options of the Zarr backend study the {@link N5ZarrWriter}
   * constructors.
   *
   * @param path
   * @return
   * @throws IOException
   */
  public N5ZarrWriter openZarrWriter(final String path) throws IOException {

	return new N5ZarrWriter(path, gsonBuilder, zarrDimensionSeparator, zarrMapN5DatasetAttributes);
  }

  /**
   * Open an {@link N5Writer} for HDF5.  Don't forget to close the writer
   * after writing to close the file and make it available to other
   * processes.
   *
   * For more options of the HDF5 backend study the {@link N5HDF5Writer}
   * constructors.
   *
   * @param path
   *
   * @return
   * @throws IOException
   */
  public N5HDF5Writer openHDF5Writer(final String path) throws IOException {

	return new N5HDF5Writer(path, hdf5OverrideBlockSize, gsonBuilder, hdf5DefaultBlockSize);
  }

  /**
   * Open an {@link N5Writer} for Google Cloud.
   *
   * @param url
   * @return
   * @throws IOException
   */
  public N5GoogleCloudStorageWriter openGoogleCloudWriter(final String url) throws IOException {

	final GoogleCloudStorageClient storageClient;
	if (googleCloudProjectId == null) {
	  final ResourceManager resourceManager = new GoogleCloudResourceManagerClient().create();
	  final Iterator<Project> projectsIterator = resourceManager.list().iterateAll().iterator();
	  if (!projectsIterator.hasNext())
		return null;
	  storageClient = new GoogleCloudStorageClient(projectsIterator.next().getProjectId());
	} else
	  storageClient = new GoogleCloudStorageClient(googleCloudProjectId);

	final Storage storage = storageClient.create();
	final GoogleCloudStorageURI googleCloudUri = new GoogleCloudStorageURI(url);
	return new N5GoogleCloudStorageWriter(
			storage,
			googleCloudUri.getBucket(),
			googleCloudUri.getKey(),
			gsonBuilder);
  }

  /**
   * Open an {@link N5Writer} for AWS S3.
   *
   * @param url
   * @return
   * @throws IOException
   */
  public N5AmazonS3Writer openAWSS3Writer(final String url) throws IOException {

	return new N5AmazonS3Writer(
			createS3(url),
			new AmazonS3URI(url),
			gsonBuilder);
  }

  /**
   * Open an {@link N5Reader} based on some educated guessing from the url.
   *
   * @param url
   * @return
   * @throws IOException
   */
  public N5Reader openReader(final String url) throws IOException {

	try {
	  final URI uri = new URI(url);
	  final String scheme = uri.getScheme();
	  if (scheme == null)
		;
	  else if (scheme.equals("s3"))
		return openAWSS3Reader(url);
	  else if (scheme.equals("gs"))
		return openGoogleCloudReader(url);
	  else if (scheme.equals("https") || scheme.equals("http")) {
		if (uri.getHost().matches(".*s3\\.amazonaws\\.com"))
		  return openAWSS3Reader(url);
		else if (uri.getHost().matches(".*cloud\\.google\\.com"))
		  return openGoogleCloudReader(url);
	  }
	} catch (final URISyntaxException e) {
	}
	if (isHDF5(url))
	  return openHDF5Reader(url);
	else if (url.matches("(?i).*\\.zarr"))
	  return openZarrReader(url);
	else
	  return openFSReader(url);
  }

  /**
   * Open an {@link N5Writer} based on some educated guessing from the url.
   *
   * @param url
   * @return
   * @throws IOException
   */
  public N5Writer openWriter(final String url) throws IOException {

	try {
	  final URI uri = new URI(url);
	  final String scheme = uri.getScheme();
	  if (scheme == null)
		;
	  else if (scheme.equals("s3"))
		return openAWSS3Writer(url);
	  else if (scheme.equals("gs"))
		return openGoogleCloudWriter(url);
	  else if (scheme.equals("https") || scheme.equals("http")) {
		if (uri.getHost().matches(".*s3\\.amazonaws\\.com"))
		  return openAWSS3Writer(url);
		else if (uri.getHost().matches(".*cloud\\.google\\.com"))
		  return openGoogleCloudWriter(url);
	  }
	} catch (final URISyntaxException e) {
	}
	if (isHDF5(url))
	  return openHDF5Writer(url);
	else if (url.matches("(?i).*\\.zarr"))
	  return openZarrWriter(url);
	else
	  return openFSWriter(url);
  }
}
