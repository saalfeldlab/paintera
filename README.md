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
java -Xmx16G -jar target/bigcat-0.0.3-SNAPSHOT-jar-with-dependencies.jar -i <input_hdf_file>
```

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

