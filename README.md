# BIGCAT (working title)

[![Build Status](https://travis-ci.org/ssinhaleite/bigcat.svg?branch=javafx-generic-listeners)](https://travis-ci.org/ssinhaleite/bigcat)

## Dependences

* javafx (ubuntu):

```shell
sudo apt install openjfx
```

* zeromq (ubuntu):

```shell
sudo apt install libzmq3-dev
```
* [jzmq](https://github.com/zeromq/jzmq)

* branchs (download and compile each one of them: `mvn clean install`):
	* [imglib2-ui:2.0.0-beta-34-SNAPSHOT - branch: javafx](https://github.com/hanslovsky/imglib2-ui/tree/javafx)
	* [bdv-core:4.3.1-SNAPSHOT - branch: bigcat-javafx](https://github.com/hanslovsky/bigdataviewer-core/tree/bigcat-javafx)

* [imglib2:4.6.0-SNAPSHOT](https://github.com/imglib/imglib2.git)

* [ClearGL:2.1.0](https://github.com/ClearVolume/ClearGL.git)

* [bdv-vistools:1.0.0-beta-8-SNAPSHOT](https://github.com/bigdataviewer/bigdataviewer-vistools.git)

## Compile

run:

```shell
mvn clean install
```

or, to generate a "fat jar" with all dependencies added, run:

```shell
mvn clean compile assembly:single
```

## Run

```shell
java -Xmx16G -jar target/bigcat-0.0.3-SNAPSHOT-jar-with-dependencies.jar
```

or you can download a compiled fat jar from [here](https://www.dropbox.com/s/ouhmymh5k057mdg/bigcat-0.0.3-SNAPSHOT-jar-with-dependencies-07112017.jar?dl=0).

Parameters:

| Option                  | Description        | Default value             |
| ----------------------- |:------------------:|:-------------------------:|
| `--file` or `-f`        | input file path (hdf5) | USER_HOME + /Downloads/sample_A_padded_20160501.hdf |
| `--label` or `-l`       | label dataset name | volumes/labels/neuron_ids |
| `--raw` or `-r`         | raw dataset name   | volumes/raw               |
| `--resolution` or `-rs` | resolution         | { 4, 4, 40 }              |
| `--rawCellSize` or `-rcs` | raw cell size    | { 192, 96, 7 }            |
| `--labelCellSize` or `-lcs`| label cell size | { 79, 79, 4 }             |

The default dataset can be downloaded from the [Cremi challenge website](https://cremi.org/static/data/sample_A_padded_20160501.hdf)(1.47Gb)

## Usage

First, it is necessary to activate the labels 
press `ctrl` + `tab` until "labels" get selected in the right panel.

There are four modes:
* Navigation only:

* Highlights:

   In this mode, you can use `shift` + `left click` to select any neuron. Use `shift` + `right click` to select more than one neuron.

* Merge and split:

* Render 3D:

   In this mode, you can use the same commands of highlight mode (`shift` + `left click` or `shift` + `right click`) to generate the 3d shape of the selected(s) neuron(s).
   In this mode, to generate a mesh does not imply in highlight the neuron on the other views.

## Development

[![Join the chat at https://gitter.im/saalfeldlab/bigcat](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/saalfeldlab/bigcat?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
Collaborative volume annotation and segmentation with BigDataViewer

## Tests

### Dependencies
* Junit

### Run

To run all tests:
```
mvn clean test
```

