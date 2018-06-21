# Paintera

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

## Run

```shell
java -Xmx16G -XX:+UseConcMarkSweepGC -jar target/paintera-0.1.0-SNAPSHOT-shaded.jar
```
Replace `16G` with the maximum amount of memory that Paintera should use.

#### Display help message and command line parameters
```shell
$ java -jar target/paintera-0.1.0-SNAPSHOT-shaded.jar --help
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

## Data

In [#61](https://github.com/saalfeldlab/paintera/issues/61) we introduced a specification for the data format that Paintera can load through the opener dialog (`Ctrl O`).
These restrictions hold only for the graphical user interface. If desired, callers can
 - add arbitrary data sets programatically, or
 - through the `attributes.json` project file if an appropriate gson deserializer is supplied.

### Raw
Accept any of these:
 1. any regular (i.e. default mode) three-dimensional N5 dataset that is integer or float. Optional attributes are `"resolution": [x,y,z]` and `"offset": [x,y,z]`.
 2. any multiscale N5 group that has `"multiScale" : true` attribute and contains three-dimensional multi-scale datasets `s0` ... `sN`. Optional attributes are `"resolution": [x,y,z]` and `"offset: [x,y,z]"`. In addition to the requirements from (1), all `s1` ... `sN` datasets must contain `"downsamplingFactors": [x,y,z]` entry (`s0` is exempt, will default to `[1.0, 1.0, 1.0]`). All datasets must have same type. Optional attributes from (1) will be ignored.
 3. (preferred) any N5 group with attribute `"painteraData : {"type" : "raw"}` and a dataset/group `data` that conforms with (1) or (2).

### Labels
Accept any of these:
 1. any regular (i.e. default mode) integer or varlength `LabelMultisetType` (`"isLabelMultiset": true`) three-dimensional N5 dataset. Optional attributes are `"resolution": [x,y,z]`, `"offset": [x,y,z]`, `"maxId": <id>`. If `"maxId"` is not specified, it is determined at start-up and added.
 2. any multiscale N5 group that has `"multiScale" : true` attribute and contains three-dimensional multi-scale datasets `s0` ... `sN`. Optional attributes are `"resolution": [x,y,z]`, `"offset": [x,y,z]`, `"maxId": <id>`. If `"maxId"` is not specified, it is determined at start-up and added (this can be expensive). In addition to the requirements from (1), all `s1` ... `sN` datasets must contain `"downsamplingFactors": [x,y,z]` entry (`s0` is exempt, will default to `[1.0, 1.0, 1.0]`). All datasets must have same type. Optional attributes from (1) will be ignored.
 3. (preferred) any N5 group with attribute `"painteraData : {"type" : "label"}` and a dataset/group `data` that conforms with (1) or (2). Optional sub-groups are:
   - `fragment-segment-assignment` -- Dataset to store fragment-segment lookup table. Can be empty or will be initialized empty if it does not exist.
   - `unique-label-lists`          -- Multiscale varlength dataset with same 'dimensions'/'blockSize' and as dataset(s) in `data`. Holds unique block lists from which relevant blocks for specific ids are retrieved.

#### Things to consider for labels:
 - make `"maxId"` attribute mandatory because `IdService` needs it and would require to scan whole dataset (can be huge)
 - Efficient mesh generation
  - only possible if
    - option (3) and `unique-label-lists` exists, or
    - `LabelMultisetType` dataset (currently, `VolatileLabelMultisetArray` holds a set of contained labels).
  - If none of these are true, ask user if
    - unique label lists should be generated from highest resolution to lowest resolution (slow, would need to be generated once and cached or saved as `unique-label-lists` if option (3), probably useful for looking at small datasets), or
    - generate unique label lists on the fly from lowest resolution to highest resolution (fast, only do the work that's currently necessary, but potentially incomplete), or
    - 3D support should be disabled for that source.
 - If (1) or (2), fragment-segment-assignment cannot be committed back to source (only stored as actions in project's `attributes.json`). There should be an option to export fragment-segment-assignment as N5 dataset. That way, users can choose to update their source in order to conform with (3) and not lose their work on fragment-segment assignments
 - (3) leaves room for adding related data in the future, e.g. annotations



