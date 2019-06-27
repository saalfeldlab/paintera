[![Build Status](https://travis-ci.org/saalfeldlab/paintera.svg?branch=master)](https://travis-ci.org/saalfeldlab/paintera)

![Paintera example with meshes for multiple neurons and synapses](data/paintera-example-with-synapses.png?raw=true "Paintera")

# Paintera

Paintera is a general visualization tool for 3D volumetric data and proof-reading in segmentation/reconstruction with a primary focus on neuron reconstruction from [electron micrographs](http://www.rsc.org/publishing/journals/prospect/ontology.asp?id=CMO:0001800&MSID=B310802C) in [connectomics](https://en.wikipedia.org/wiki/Connectomics). It features/supports (unchecked boxes mean planned but not yet implemented)
 - [x] Views of orthogonal 2D cross-sections of the data at arbitrary angles and zoom levels
 - [x] [Mipmaps](https://en.wikipedia.org/wiki/Mipmap) for efficient display of arbitrarily large data at arbitrary scale levels
 - [x] Label data
   - [x] Painting
   - [x] Manual agglomeration
   - [x] 3D visualization as polygon meshes
      - [x] Meshes for each mipmap level
      - [x] Mesh generation on-the-fly via marching cubes to incorporate painted labels and agglomerations in 3D visualization. Marching Cubes is parallelized over small blocks. Only relevant blocks are considered (huge speed-up for sparse label data).
      - [ ] Adaptive mesh details, i.e. only show high-resolution meshes for blocks that are closer to camera.
      
Paintera is implemented in [Java](https://openjdk.java.net/) and makes extensive use of the UI framework [JavaFX](https://openjfx.io/)

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

On Ubuntu 18.04, you will have to install OpenJFX 8 explicitly and then immediately pin this version:

```shell
sudo apt install openjfx=8u161-b12-1ubuntu2 libopenjfx-java=8u161-b12-1ubuntu2 libopenjfx-jni=8u161-b12-1ubuntu2
sudo apt-mark hold openjfx libopenjfx-java libopenjfx-jni
```

If your are on 18.10 or newer, add the bionic repositories following instruction on https://bugs.launchpad.net/ubuntu/+source/openjfx/+bug/1799946.

Alternatively, OpenJDK and maven are available through the default (`pkgs/main`) and `conda-forge` channels on [conda](https://conda.io), respectively. 
```sh
conda install -c pkgs/main openjdk
conda install -c conda-forge maven
```
Note that OpenJDK on the `conda-forge` channels does not ship with JavaFX and cannot be used.

## Compile

To compile and install the Paintera jar into your local maven repository, run
```shell
mvn clean install
```
Note that the fat jar profile is not supported anymore.

## Install
```bash
conda install -c conda-forge -c hanslovsky paintera
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

To run a current *SNAPSHOT* version or your own modified Paintera, use [jgo](https://github.com/scijava/jgo) to run your [locally compiled jar](https://github.com/saalfeldlab/paintera/#compile)
```shell
jgo [JGO ARG...] [JVM ARG...] org.janelia.saalfeldlab:paintera:0.8.2-SNAPSHOT
```
or with logger bindings:
```shell
jgo [JGO ARG...] [JVM ARG...] org.janelia.saalfeldlab:paintera:0.8.2-SNAPSHOT+org.slf4j:slf4j-simple:1.7.25
```

We recommend these Java options:

|Option| Description|
| ---- | ---------- |
| -Xmx16G | Maximum Java heap space (replace 16G with desired amount) |
| -XX:+UseConcMarkSweepGC | concurrent garbage collector generally better for UI applications |

#### Display help message and command line parameters
```shell
$ paintera --help
Usage: Paintera [-h] [--default-to-temp-directory] [--print-error-codes]
                [--version] [--height=HEIGHT]
                [--highest-screen-scale=HIGHEST_SCREEN_SCALE]
                [--num-screen-scales=NUM_SCREEN_SCALES]
                [--screen-scale-factor=SCREEN_SCALE_FACTOR] [--width=WIDTH]
                [--screen-scales=SCREEN_SCALES[,SCREEN_SCALES...]...]...
                [PROJECT]
      [PROJECT]             Optional project N5 root (N5 or FileSystem).
      --default-to-temp-directory
                            Default to temporary directory instead of showing dialog
                              when PROJECT is not specified.
      --height=HEIGHT       Initial height of viewer. Defaults to 600. Overrides
                              height stored in project.
      --highest-screen-scale=HIGHEST_SCREEN_SCALE
                            Highest screen scale, restricted to the interval (0,1],
                              defaults to 1. If no scale option is specified, scales
                              default to [1.0, 0.5, 0.25, 0.125, 0.0625].
      --num-screen-scales=NUM_SCREEN_SCALES
                            Number of screen scales, defaults to 3. If no scale
                              option is specified, scales default to [1.0, 0.5,
                              0.25, 0.125, 0.0625].
      --print-error-codes   List all error codes and exit.
      --screen-scale-factor=SCREEN_SCALE_FACTOR
                            Scalar value from the open interval (0,1) that defines
                              how screen scales diminish in each dimension. Defaults
                              to 0.5. If no scale option is specified, scales
                              default to [1.0, 0.5, 0.25, 0.125, 0.0625].
      --screen-scales=SCREEN_SCALES[,SCREEN_SCALES...]...
                            Explicitly set screen scales. Must be strictly
                              monotonically decreasing values in from the interval
                              (0,1]. Overrides all other screen scale options. If no
                              scale option is specified, scales default to [1.0,
                              0.5, 0.25, 0.125, 0.0625].
      --version             Print version string and exit
      --width=WIDTH         Initial width of viewer. Defaults to 800. Overrides
                              width stored in project.
  -h, --help                Display this help message.
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
| right click / `Ctrl` left click | toggle id under cursor if current source is label source (append to current selection) |
| `Shift` left click | Merge id under cursor with id that was last toggled active (if any) |
| `Shift` right click | Split id under cursor from id that was last toggled active (if any) |
| `Space` left click/drag | Paint with id that was last toggled active (if any) |
| `Space` right click/drag | Erase within canvas only |
| `Shift` + `Space` right click/drag | Paint background label |
| `Space` wheel | change brush size |
| `F` + left click | 2D Flood-fill in current viewer plane with id that was last toggled active (if any) |
| `Shift` + `F` + left click | Flood-fill with id that was last toggled active (if any) |
| `N` | Select new, previously unused id |
| `S` | Enter shape interpolation mode |
| `1` / `2` | Edit first/second section when previewing interpolated shape |
| `Enter` | Commit interpolated shape into canvas |
| `Esc` | Abort shape interpolation mode |
| `Ctrl` + `C` | Show dialog to commit canvas and/or assignments |
| `C` | Increment ARGB stream seed by one |
| `Shift` + `C` | Decrement ARGB stream seed by one |
| `Ctrl` + `Shift` + `C` | Show ARGB stream seed spinner |
| `Ctrl` + `Enter` | Merge all selected ids |
| `V` | Toggle visibility of current source |
| `Shift` + `V` | Toggle visibility of not-selected ids in current source (if label source) |
| `R` | Clear mesh caches and refresh meshes (if current source is label source) |
| `L` | Lock last selected segment (if label source) |
| `Ctrl` + `S` | Save current project state |
| `Ctrl` + `Shift` + `N` | Create new label dataset |
| `Ctrl` + `T` | Threshold raw source (only available if current source is raw source) |

### Shape interpolation mode

The mode is activated by pressing the `S` key when the current source is a label source. Then, you can select the objects in the sections by left/right clicking (scrolling automatically fixes the selection in the current section).

When you're done with selecting the objects in the second section and initiate scrolling, the preview of the interpolated shape will be displayed. If something is not right, you can edit the selection in the first or second section by pressing `1` or `2`, which will update the preview. When the desired result is reached, hit `Enter` to commit the results into the canvas and return back to normal mode.

While in the shape interpolation mode, at any point in time you can hit `Esc` to discard the current state and exit the mode.

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
