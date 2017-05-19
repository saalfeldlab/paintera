# BIGCAT (working title)

## Compile

To compile a "fat jar" with all dependencies added, run:

```shell
mvn clean compile assembly:single
```

## Run

```shell
java -Xmx16G -jar target/bigcat-0.0.1-SNAPSHOT-jar-with-dependencies.jar -i <input_hdf_file>
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

###Annotations Test

* creation of annotation
* removal of annotation
* changes in visibility of annotation overlay

```
mvn clean test -Dtest=bdv.bigcat.AnnotationsTest.java
```

