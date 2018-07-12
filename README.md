[![Build Status](https://travis-ci.org/saalfeldlab/paintera.svg?branch=master)](https://travis-ci.org/saalfeldlab/paintera)

# Paintera

**IMPORTANT NOTE** If you [install Paintera through conda](https://github.com/saalfeldlab/paintera#install) you will have to use your system Java and JavaFX to be able to run Paintera because there is no JavaFX package on conda at the moment.

TBD

## Dependences

* java (ubuntu):
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

## Compile

run:

```shell
mvn clean install
```

or, to generate a "fat jar" with all dependencies added, run:

```shell
mvn -Pfat clean package
```
This will include `org.slf4j:slf4j-simple` as `slf4j` binding.

## Install
```bash
conda install -c hanslovsky paintera
```

## Run
```bash
paintera [[JRUN ARG...] [JVM ARG...] --] [ARG...]
```
Jrun/JVM args are separated by `--`. The default max heap size is half the available system memory. To change the heap size, run
```bash
paintera -Xmx<size> -- [ARG...]
```
or
```bash
PAINTERA_MAX_HEAP_SIZE=<size> paintera [ARG...]
```

By default, `org.slf4j:slf4j-simple` is used as `slf4j` binding for logging. This can be replaced by setting the `PAINTERA_SLF4J_BINDING` environment variable:
```bash
PAINTERA_SLF4J_BINDING=groupId:artifactId[:version]
```
For example, to replace `org.slf4j:slf4j-simple` with `ch.qos.logback:logback-classic`, run (on Linux/OSX):
```
PAINTERA_SLF4J_BINDING=ch.qos.logback:logback-classic paintera
```

You can also run a [compiled fat jar](https://github.com/saalfeldlab/paintera/#compile)
```shell
java <java-opts> -jar target/paintera-0.1.2-SNAPSHOT-shaded.jar
```
We recommend these Java options:

|Option| Description|
| ---- | ---------- |
| -Xmx16G | Maximum Java heap space (replace 16G with desired amount) |
| -XX:+UseConcMarkSweepGC | concurrent garbage collector generally better for UI applications |

#### Display help message and command line parameters
```shell
$ java -jar target/paintera-0.1.1-SNAPSHOT-shaded.jar --help
Usage: Paintera [-h] [--height=HEIGHT] [--width=WIDTH]
                [--label-source=LABEL_SOURCE]... [--raw-source=RAW_SOURCE]...
      --height=HEIGHT   Initial height of viewer. Defaults to 600.
      --label-source=LABEL_SOURCE
                        Open label source at start-up. Has to be [file://]
                          /path/to/<n5-or-hdf5>:path/to/dataset
      --raw-source=RAW_SOURCE
                        Open raw source at start-up. Has to be [file://]
                          /path/to/<n5-or-hdf5>:path/to/dataset
      --width=WIDTH     Initial width of viewer. Defaults to 800.
  -h, --help            Display this help message.
```

## Usage

| Action | Description |
| --------------- | ----------- |
| `P`               | Show Status bar on right side |
| (`Shift` +) `Ctrl` + `Tab` | Cycle current source forward (backward) |
| `Ctrl` + `O` | Show open dataset dialog |
| `M` | Maximize current view |
| `Shift` + `M` | Maximize split view of one slicing viewer and 3D scene |
| `Shift` + `Z` | Un-rotate but keep scale and translation |
| left click | toggle id under cursor if current source is label source (de-select all others) |
| right click | toggle id under cursor if current source is label source (append to current selection) |
| `Shift` left click | Merge id under cursor with id that was last toggled active (if any) |
| `Shift` right click | Split id under cursor from id that was last toggled active (if any) |
| `Space` left click/drag | Paint with id that was last toggled active (if any) |
| `Space` right click/drag | Erase within canvas only |
| `Shift` + `Space` right click/drag | Paint background label |
| `Space` wheel | change brush size |
| `F` + left click | 2D Flood-fill in current viewer plane with id that was last toggled active (if any) |
| `Shift` + `F` + left click | Flood-fill with id that was last toggled active (if any) |
| `N` | Select new, previously unused id |
| `Ctrl` + `C` | Show dialog to commit canvas and/or assignments |
| `C` | Increment ARGB stream seed by one |
| `Shift` + `C` | Decrement ARGB stream seed by one |
| `Ctrl` + `Shift` + `C` | Show ARGB stream seed spinner |
| `V` | Toggle visibility of current source |
| `Shift` + `V` | Toggle visibility of not-selected ids in current source (if label source) |
| `R` | Clear mesh caches and refresh meshes (if current source is label source) |
| `L` | Lock last selected segment (if label source) |
| `Ctrl` + `S` | Save current project state |

## Data

In [#61](https://github.com/saalfeldlab/paintera/issues/61) we introduced a specification for the data format that Paintera can load through the opener dialog (`Ctrl O`).
These restrictions hold only for the graphical user interface. If desired, callers can
 - add arbitrary data sets programatically, or
 - through the `attributes.json` project file if an appropriate gson deserializer is supplied.

### Raw
Accept any of these:
 1. any regular (i.e. default mode) three-dimensional N5 dataset that is integer or float. Optional attributes are `"resolution": [x,y,z]` and `"offset": [x,y,z]`.
 2. any multiscale N5 group that has `"multiScale" : true` attribute and contains three-dimensional multi-scale datasets `s0` ... `sN`. Optional attributes are `"resolution": [x,y,z]` and `"offset: [x,y,z]"`. In addition to the requirements from (1), all `s1` ... `sN` datasets must contain `"downsamplingFactors": [x,y,z]` entry (`s0` is exempt, will default to `[1.0, 1.0, 1.0]`). All datasets must have same type. Optional attributes from (1) will be ignored.
 3. (preferred) any N5 group with attribute `"painteraData : {"type" : "raw"}` and a dataset/group `data` that conforms with (2).

### Labels
Accept any of these:
 1. any regular (i.e. default mode) integer or varlength `LabelMultisetType` (`"isLabelMultiset": true`) three-dimensional N5 dataset. Required attributes are `"maxId": <id>`. Optional attributes are `"resolution": [x,y,z]`, `"offset": [x,y,z]`.
 2. any multiscale N5 group that has `"multiScale" : true` attribute and contains three-dimensional multi-scale datasets `s0` ... `sN`. Required attributes are `"maxId": <id>`. Optional attributes are `"resolution": [x,y,z]`, `"offset": [x,y,z]`, `"maxId": <id>`. If `"maxId"` is not specified, it is determined at start-up and added (this can be expensive). In addition to the requirements from (1), all `s1` ... `sN` datasets must contain `"downsamplingFactors": [x,y,z]` entry (`s0` is exempt, will default to `[1.0, 1.0, 1.0]`). All datasets must have same type. Optional attributes from (1) will be ignored.
 3. (preferred) any N5 group with attribute `"painteraData : {"type" : "label"}` and a dataset/group `data` that conforms with (2). Required attributes are `"maxId": <id>`. Optional sub-groups are:
   - `fragment-segment-assignment` -- Dataset to store fragment-segment lookup table. Can be empty or will be initialized empty if it does not exist.
   - `label-to-block-mapping`      -- Multiscale directory tree with one text files per id mapping ids to containing label: `label-to-block-mapping/s<scale-level>/<id>`. If not present, no meshes will be generated.
   - `unique-labels`               -- Multiscale N5 group holding unique label lists per block. If not present (or not using `N5FS`), meshes will not be updated when commiting canvas.
