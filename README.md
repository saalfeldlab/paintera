# BIGCAT (working title)

[![Build Status](https://travis-ci.org/ssinhaleite/bigcat.svg?branch=javafx-generic-listeners)](https://travis-ci.org/ssinhaleite/bigcat)

![screenshot](https://raw.githubusercontent.com/ssinhaleite/bigcat/javafx-generic-listeners/img/bigcat-20171116.png)

## Dependences

* java
```shell
sudo apt install default-jre default-jdk
```

* maven (ubuntu):
```shell
sudo apt install maven
```

* javafx (ubuntu):

```shell
sudo apt install openjfx
```

* zeromq (ubuntu):

```shell
sudo apt install libzmq3-dev
```
* [jzmq](https://github.com/zeromq/jzmq)

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
java -Xmx16G -XX:+UseConcMarkSweepGC -jar target/bigcat-0.0.3-SNAPSHOT-jar-with-dependencies.jar
```

or you can download a compiled fat jar from [here](https://www.dropbox.com/s/f82nfc0cbrdmtji/bigcat-0.0.3-SNAPSHOT-jar-with-dependencies-08112017.jar?dl=0).

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

First, it is necessary to activate the labels by pressing `ctrl` + `tab`.

There are three modes:
* Navigation only:

* Highlights:

   In this mode, you can use `left click` to select any neuron. Use `shift` + `left click` to select more than one neuron.
   Every selected neuron will be highlighted on the 2d panels and its 3d shape will be generated.

* Merge and split:

Useful commands:
| Command                  | Description        |
| ----------------------- |:------------------:|
| `ctrl` + `p` | snapshoot of the 3d scene |
| `shift` + `z` | reset the position of the viewer |
| `shift` + `right click` (on a 3d mesh)| menu to "export mesh" and to "center ortho slices at the position" |


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

