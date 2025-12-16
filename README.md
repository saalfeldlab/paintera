
# Paintera 
[![Build Status](https://github.com/saalfeldlab/paintera/actions/workflows/build.yml/badge.svg)](https://github.com/saalfeldlab/paintera/actions/workflows/build.yml)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.11187904.svg)](https://doi.org/10.5281/zenodo.11187904)
[![GitHub Release](https://img.shields.io/github/v/release/saalfeldlab/paintera)](https://github.com/saalfeldlab/paintera/releases)


![Paintera example with meshes for multiple neurons and synapses](https://github.com/saalfeldlab/paintera/blob/6226b9cbdeaeaa22a5f4c5088c3d2cc83646b143/img/social-preview-1280.png "Paintera")

Paintera is a general visualization tool for 3D volumetric data and proof-reading in segmentation/reconstruction with a primary focus on neuron reconstruction from [electron micrographs](http://www.rsc.org/publishing/journals/prospect/ontology.asp?id=CMO:0001800&MSID=B310802C) in [connectomics](https://en.wikipedia.org/wiki/Connectomics). It features/supports:
- [x] Views of orthogonal 2D cross-sections of the data at arbitrary angles and zoom levels
- [x] [Mipmaps](https://en.wikipedia.org/wiki/Mipmap) for efficient display of arbitrarily large data at arbitrary scale levels
- [x] Label data
  - [x] Painting in arbitrary 3D orientation (not just ortho-slices)
    - [x] Paint Brush
    - [x] 2D and 3D flood fill
    - [x] [Segment Anything](https://segment-anything.com/) aided semi-automatic annotation :exclamation:
    - [x] Rapid 3D sculpting with interactive shape interpolation combining all of the above
  - [x] Manual agglomeration
  - [x] 3D visualization as polygon meshes
    - [x] Meshes for each mipmap level
    - [x] Mesh generation on-the-fly via marching cubes to incorporate painted labels and agglomerations in 3D visualization. Marching Cubes is parallelized over small blocks. Only relevant blocks are considered (huge speed-up for sparse label data).
    - [x] Adaptive mesh details, i.e. only show high-resolution meshes for blocks that are closer to camera.

## Installation

The latest release can be installed from the [Github Releases](https://github.com/saalfeldlab/paintera/releases)

[![GitHub Release](https://img.shields.io/github/v/release/saalfeldlab/paintera)](https://github.com/saalfeldlab/paintera/releases)



<details>
<summary><b>Building Paintera</b></summary>

## Building from source

### Dependencies

Paintera requires Java (JDK 21) and Maven to be installed. The following are a few ways to install them:

<details>
<summary><b>Conda</b></summary>

OpenJDK 21 and Maven are available through `conda-forge` channel on [conda](https://conda.io), respectively.
```sh
conda install -c conda-forge openjdk maven
```
</details>

<details>
<summary><b>SdkMan</b></summary>


[sdkman](https://sdkman.io/install) can be used to manage the appropriate java and maven versions. Install sdkman as follows:

*Note*: If using windows, the following sdk commands must be run via either [WSL](https://docs.microsoft.com/en-us/windows/wsl/install-win10), [Cygwin](https://www.cygwin.com/install.html) or [Git Bash For Windows](https://git-scm.com/download/win). For Windows installation instructions, please follow the [Windows Installtion](https://sdkman.io/install) instructions.

```shell
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

Once sdkman is installed, you can install the appropriate java version with:
```shell
sdk install java 21.0.2-open
```
`sdk` will then prompt whether to change to that version of java for your system default. If you say "No", then you will want to specify that you'd like to use Java 11 locally in your paintera directory. To do this, navigate to the `paintera` directory cloned in the above step, and execute:
```shell
sdk use java 21.0.2-open
# verify active version
sdk current java
```

Similarly, you can install maven via `sdkman` if you desire:
```shell
sdk install maven
```

</details>

<details>
<summary><b>Manually</b></summary>

Java 21 (through [OpenJDK](https://openjdk.java.net/)) and Apache Maven are available for installation on many Linux distributions.

On Windows and macOS the use of [Oracle Java 11](https://www.oracle.com/java/technologies/downloads/#java21) is recommended and [Apache Maven needs to be downloaded and installed](https://maven.apache.org) manually. Make sure that both Java and Maven are available on the `PATH` after installation. Note that our experience with Windows and macOS installations is very limited and there may be better ways to install Java and Maven on these operating systems. If you are aware of any, please create a pull request to add these to this README.
</details>

### Running from source

Clone the paintera git repository:
```sh
git clone https://github.com/saalfeldlab/paintera.git
cd paintera
```

To run paintera, execute the follow maven goal:
```sh
mvn javafx:run
```
</details>

## Usage
### Display help message and command line parameters
The following assumes that the `paintera` command is available on the command line.
```shell
$ paintera --help
Usage: Paintera [--add-n5-container=<container>...
                [--add-n5-container=<container>...]... (-d=DATASET...
                [-d=DATASET...]... [-r=RESOLUTION] [-r=RESOLUTION]...
                [-o=OFFSET] [-o=OFFSET]... [-R] [--min=MIN] [--max=MAX]
                [--channel-dimension=CHANNEL_DIMENSION]
                [--channels=CHANNELS...] [--channels=CHANNELS...]...
                [--name=NAME] [--name=NAME]...
                [--id-service-fallback=ID_SERVICE_FALLBACK]
                [--label-block-lookup-fallback=LABEL_BLOCK_LOOKUP_FALLBACK]
                [--entire-container] [--exclude=EXCLUDE...]
                [--exclude=EXCLUDE...]... [--include=INCLUDE...]
                [--include=INCLUDE...]... [--only-explicitly-included])]...
                [-h] [--default-to-temp-directory] [--print-error-codes]
                [--version] [--height=HEIGHT]
                [--highest-screen-scale=HIGHEST_SCREEN_SCALE]
                [--num-screen-scales=NUM_SCREEN_SCALES]
                [--screen-scale-factor=SCREEN_SCALE_FACTOR] [--width=WIDTH]
                [--screen-scales=SCREEN_SCALES[,SCREEN_SCALES...]...]...
                [PROJECT]
      [PROJECT]              Optional project N5 root (N5 or FileSystem).
      --add-n5-container=<container>...
                             Container of dataset(s) to be added. If none is
                               provided, default to Paintera project (if any).
                               Currently N5 file system and HDF5 containers are
                               supported.
      --channel-dimension=CHANNEL_DIMENSION
                             Defines the dimension of a 4D dataset to be
                               interpreted as channel axis. 0 <=
                               CHANNEL_DIMENSION <= 3
                               Default: 3
      --channels=CHANNELS... Use only this subset of channels for channel (4D)
                               data. Multiple subsets can be specified. If no
                               channels are specified, use all channels.
  -d, --dataset=DATASET...   Dataset(s) within CONTAINER to be added. TODO: If
                               no datasets are specified, all datasets will be
                               added (or use a separate option for this).
      --default-to-temp-directory
                             Default to temporary directory instead of showing
                               dialog when PROJECT is not specified.
      --entire-container     If set to true, discover all datasets (Paintera
                               format, multi-scale group, and N5 dataset)
                               inside CONTAINER and add to Paintera. The -d,
                               --dataset and --name options will be ignored if
                               ENTIRE_CONTAINER is set. Datasets can be
                               excluded through the --exclude option. The
                               --include option overrides any exclusions.
      --exclude=EXCLUDE...   Exclude any data set that matches any of EXCLUDE
                               regex patterns.
  -h, --help                 Display this help message.
      --height=HEIGHT        Initial height of viewer. Defaults to 600.
                               Overrides height stored in project.
                               Default: -1
      --highest-screen-scale=HIGHEST_SCREEN_SCALE
                             Highest screen scale, restricted to the interval
                               (0,1], defaults to 1. If no scale option is
                               specified, scales default to [1.0, 0.5, 0.25,
                               0.125, 0.0625].
      --id-service-fallback=ID_SERVICE_FALLBACK
                             Set a fallback id service for scenarios in which
                               an id service is not provided by the data
                               backend, e.g. when no `maxId' attribute is
                               specified in an N5 dataset. Valid options are
                               (case insensitive): from-data — infer the max id
                               and id service from the dataset (may take a long
                               time for large datasets), none — do not use an
                               id service (requesting new ids will not be
                               possible), and ask — show a dialog to choose
                               between those two options
                               Default: ask
      --include=INCLUDE...   Include any data set that matches any of INCLUDE
                               regex patterns. Takes precedence over EXCLUDE.
      --label-block-lookup-fallback=LABEL_BLOCK_LOOKUP_FALLBACK
                             Set a fallback label block lookup for scenarios in
                               which a label block lookup is not provided by
                               the data backend. The label block lookup is used
                               to process only relevant data during on-the-fly
                               mesh generation. Valid options are: `complete' —
                               always process the entire dataset (slow for
                               large data), `none' — do not process at all (no
                               3D representations/meshes available), and `ask'
                               — show a dialog to choose between those two
                               options
                               Default: ask
      --max=MAX              Maximum value of contrast range for raw and
                               channel data.
      --min=MIN              Minimum value of contrast range for raw and
                               channel data.
      --name=NAME            Specify name for dataset(s). The names are
                               assigned to datasets in the same order as
                               specified. If more datasets than names are
                               specified, the remaining dataset names will
                               default to the last segment of the dataset path.
      --num-screen-scales=NUM_SCREEN_SCALES
                             Number of screen scales, defaults to 3. If no
                               scale option is specified, scales default to
                               [1.0, 0.5, 0.25, 0.125, 0.0625].
  -o, --offset=OFFSET        Spatial offset for all dataset(s) specified by
                               DATASET. Takes meta-data over resolution
                               specified in meta data of DATASET
      --only-explicitly-included
                             When this option is set, use only data sets that
                               were explicitly included via INCLUDE. Equivalent
                               to --exclude '.*'
      --print-error-codes    List all error codes and exit.
  -r, --resolution=RESOLUTION
                             Spatial resolution for all dataset(s) specified by
                               DATASET. Takes meta-data over resolution
                               specified in meta data of DATASET
  -R, --revert-array-attributes
                             Revert array attributes found in meta data of
                               attributes of DATASET. Does not affect any array
                               attributes set explicitly through the RESOLUTION
                               or OFFSET options.
      --screen-scale-factor=SCREEN_SCALE_FACTOR
                             Scalar value from the open interval (0,1) that
                               defines how screen scales diminish in each
                               dimension. Defaults to 0.5. If no scale option
                               is specified, scales default to [1.0, 0.5, 0.25,
                               0.125, 0.0625].
      --screen-scales=SCREEN_SCALES[,SCREEN_SCALES...]...
                             Explicitly set screen scales. Must be strictly
                               monotonically decreasing values in from the
                               interval (0,1]. Overrides all other screen scale
                               options. If no scale option is specified, scales
                               default to [1.0, 0.5, 0.25, 0.125, 0.0625].
      --version              Print version string and exit
      --width=WIDTH          Initial width of viewer. Defaults to 800.
                               Overrides width stored in project.
                               Default: -1
```

## Tutorial videos:
* [10 minute overview of Paintera](https://www.youtube.com/watch?v=rNJotgwUYqc)
* [1 hour in-depth Paintera tutorial](https://www.youtube.com/watch?v=ZDcK0aCLoRc)

## Control Shortcuts
| Action                   | Description                                                                                                                                                       |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Window Controls**      |                                                                                                                                                                   |
| `P`                      | Toggle visibility of side panel menu on right hand side                                                                                                           |
| `T`                      | Toggle visibility of tool bar                                                                                                                                     |
| `Shift` + `D`            | Detach/Reattach current focused view into a separate window                                                                                                       |
| `Ctrl` + `Shift` + `D`   | Reset all view windows to default position (3 orthogonal views in one window, with a 3D view as well)                                                             |
| `F2`                     | Toggle menu bar visibility                                                                                                                                        |
| `Shift` + `F2`           | Toggle menu bar mode (overlay views, or above views)                                                                                                              |
| `F3`                     | Toggle status bar visibility                                                                                                                                      |
| `Shift` + `F3`           | Toggle status bar mode (overlay views, or below views)                                                                                                            | `P`                      | Toggle visibility of side panel menu on right hand side                                                                                                           |
| `F11`                    | Toggle fullscreen                                                                                                                                                 |
| **Project Controls**     |                                                                                                                                                                   |
| `Ctrl` + `C`             | Show dialog to commit canvas and/or assignments                                                                                                                   |
| `Ctrl` + `S`             | Save current project state. <br/>**Note**: This does **not** commit/persist canvas. Use the `commit canvas` dialog to persist any painted labels across sessions. |
| `Ctrl` + `Q`             | Quit Paintera                                                                                                                                                     |
| `Shortcut` + `Alt` + `T` | Open scripting REPL                                                                                                                                               |
| **Help**                 |                                                                                                                                                                   |
| `F1`                     | Show Readme (this page)                                                                                                                                           |
| `F4`                     | Show Key bindings                                                                                                                                                 |
| **Bookmarks**            |                                                                                                                                                                   |
| `B`                      | Bookmark current location with the current view settings                                                                                                          |
| `Shift` + `B`            | Open dialog to add a location bookmark and include a text note                                                                                                    |
| `Ctrl` + `B`             | Open dialog to select a bookmarked location                                                                                                                       |

### Working with Data
| Action                    | Description                                             |
|---------------------------|---------------------------------------------------------|
| `Ctrl` + `O`              | Show open dataset dialog                                |
| `Ctrl` + `Shift` + `N`    | Create new label dataset                                |
| `V`                       | Toggle visibility of current source dataset             |
| `Ctrl` + `Tab`            | Cycle current source dataset forward                    |
| `Shift` + `Ctrl` + `Tab`  | Cycle current source dataset backward                   |

### Navigation
- Paintera shares the same [basic navigation controls as BigDataViewer](https://imagej.net/plugins/bdv/#basic-navigation)

| Action                                                       | Description                                            |
|--------------------------------------------------------------|--------------------------------------------------------|
| Mouse scroll wheel, or left/right arrow keys                 | Scroll through image z planes                          |
| `Ctrl` + `Shift` + mouse scroll wheel, or up/down arrow keys | Zoom in/out                                            |
| Right mouse click and drag                                   | Pan across image                                       |
| Left/right arrow keys                                        | Rotate view in the same plane                          |
| Left mouse click and drag                                    | Rotate view to a non-orthoslice image plane            |
| `Shift` + `Z`                                                | Reset view: un-rotate but keep scale and translation   |
| `M`                                                          | Maximize current view                                  |
| `Shift` + `M`                                                | Maximize split view of one slicing viewer and 3D scene |

### Labelling
#### Selecting Labels

| Action                               | Description                                                                                           |
|--------------------------------------|-------------------------------------------------------------------------------------------------------|
| Left click                           | toggle label id under cursor if current source is label source (de-select all others)                 |
| Right click / `Ctrl` + left click    | toggle label id under cursor if current source is label source (append to current selection)          |
| `Ctrl` + `A`                         | Select all label ids                                                                                  |
| `Ctrl` + `Shift` + `A`               | Select all label ids in current view                                                                  |
| `Shift` + `V`                        | Toggle visibility of not-selected label ids in current source dataset (if dataset is a label source)  |
#### Drawing Labels
| Action                               | Description                                                                                  |
|--------------------------------------|----------------------------------------------------------------------------------------------|
| `N`                                  | Select new, previously unused label id (you must have a label id selected to paint labels)   |
| `Space` + left click/drag            | Paint with id that was last toggled active (if any)                                          |
| `Space` + right click/drag           | Erase within canvas only                                                                     |
| `Shift` + `Space` right click/drag   | Erase commited/saved label. Paints with the background label id                              |
| `Space` + mouse scroll wheel         | Change brush size                                                                            |
| Left click                           | Select/deselect the label under the mouse cursor                                             |
#### Label ID Color Mapping
| Action                               | Description                                                         |
|--------------------------------------|---------------------------------------------------------------------|
| `C`                                  | Change label id color mapping (increments ARGB stream seed by one)  |
| `Shift` + `C`                        | Change label id color mapping (decrements ARGB stream seed by one)  |
| `Ctrl` + `Shift` + `C`               | Show ARGB stream seed spinner                                       |

#### Merge/Split Labels
| Action                               | Description                                                                |
|--------------------------------------|----------------------------------------------------------------------------|
| `Shift` + right click                | Split label id under cursor from id that was last toggled active (if any)  |
| `Shift` + left click                 | Merge label id under cursor with id that was last toggled active (if any)  |
| `Ctrl` + `Enter`                     | Merge all selected label ids                                               |

#### Flood Fill
| Action                               | Description                                                                                  |
|--------------------------------------|----------------------------------------------------------------------------------------------|
| `F` + left click                     | 2D Flood-fill in current viewer plane with label id that was last toggled active (if any)    |
| `Shift` + `F` + left click           | Flood-fill in all image planes with label id that was last toggled active (if any)           |

#### Shape Interpolation mode
- The mode is activated by pressing the `S` key when the current source is a label source. Then, you can select the objects in the sections by left/right clicking (scrolling automatically fixes the selection in the current section).

- When you're done with selecting the objects in the second section and initiate scrolling, the preview of the interpolated shape will be displayed. If something is not right, you can edit the selection in the first or second section by pressing `1` or `2`, which will update the preview. When the desired result is reached, hit `Enter` to commit the results into the canvas and return back to normal mode.
- Normal navigation controls are also available during shape interpolation, **EXCEPT** the views cannot be rotats. See [Navigation](#navigation) controls
- While in the shape interpolation mode, at any point in time you can hit `Esc` to discard the current state and exit the mode.
- Additionally, the following tools are available during shape interpolation for editing labels
  - 2D [Flood Fill](#flood-fill)
  - Segment Anything [Automatic Labelling](#automatic-labelling-segment-anything)
  - Label Selection

| Action                               | Description                                                                                |
|--------------------------------------|--------------------------------------------------------------------------------------------|
| `S`                                  | Toggle Shape Interpolation mode                                                            |
| `Esc`                                | Exit current tool, or exit Shape Interpolation mode if no tool is active                   |
| `Shift` + `Left` / `Shift` + `Right` | Move to first/last slice                                                                   |
| `Left` / `Right`                     | Move to the previous/next slice                                                            |
| `Enter`                              | Commit interpolated shape into canvas                                                      |
| `Ctrl` + `P`                         | Toggle interpolation preview                                                               |
| Left Click                           | Exclusively select the label under the cursor, **removing** all other labels at this slice |
| Right Click                          | Inclusively select the label under the cursor, **keeping** all other labels at this slice  |
| `F`                                  | Activate 2D Flood Fill Tool                                                                |
| Hold `SPACE`                         | Activate PAINT Tool                                                                        |
| `A`                                  | Activate SAM Tool                                                                          |
| `'` (Single Quote)                   | Bisect nearest two slices with new SAM auto-segmentation slice                             |
| `"` (Double Quote)                   | Bisect all slices with new SAM auto-segmentation slices                                    |
| `[`                                  | Add a new SAM auto-segmentation slices to the beginning of the current slices              |
| `]`                                  | Add a new SAM auto-segmentation slices to the end of the current slices                    |

#### Automatic Labelling: Segment Anything
- Integrates Segment Anything to predict automatic segmentations, based on the underlying image
- Moving the cursor results in real-time interactive predicted segmentations
  - These predictions are only previews until confirmed with `Left Click` or `Enter`
- Holding `Ctrl` allows you to specify include/exclude points, instead of predictions based only one the cursor position
  - **Note:** removing `Ctrl` will revert back to real-time prediction mode. If the cursor is moved, existing include/exclude points will be removed, and the cursor will again be used for the prediction.
- See [technical notes](#automatic-labelling-with-segment-anything) for more information
See [Technical Notes](#)

| Action               | Description                                                           |
|----------------------|-----------------------------------------------------------------------|
| `A`                  | Start automatic labelling mode                                        |
| Left Click / `Enter` | Paint current automatic segmentation to the canvas                    |
| `Ctrl` + Left Click  | Add point which should be **inside** of the automatic segmentation    |
| `Ctrl` + Right Click | Add point which should be **outside** of the automatic segmentation   |
| Left Click Drag      | Create box prompt for automatic segmentation                          |
| `Ctrl` + Scroll      | Increase or decrease the prediction threshold to use for segmentation |

## Supported Data

Paintera supports single and multi-channel raw data and label data from N5, Zarr, and HDF5 data, stored locally and on AWS or Google Cloud storage.
The preferred format is the Paintera data format but regular single or multi-scale datasets can be imported as well.
Any N5-like format can be converted into the preferred Paintera format with the [Paintera Conversion Helper](https://github.com/saalfeldlab/paintera-conversion-helper).
For example, to convert raw and neuron_ids of the [padded sample A](https://cremi.org/static/data/sample_A_padded_20160501.hdf) of the [CREMI](https://cremi.org) challenge, simply run (assuming the data was downloaded into the current directory):
```sh
paintera-convert to-paintera \
  --scale 2,2,1 2,2,1 2,2,1 2 2 \
  --reverse-array-attributes \
  --output-container=example.n5 \
  --container=sample_A_padded_20160501.hdf \
    -d volumes/raw \
      --target-dataset=volumes/raw2 \
      --dataset-scale 3,3,1 3,3,1 2 2 \
      --dataset-resolution 4,4,40.0 \
    -d volumes/labels/neuron_ids
```

In this example:

- `--scale` specifies the number of downsampled mipmap levels, where each comma-separated triple specifies a downsampling factor relative to the previous level. The total number of levels in the mipmap pyramid is the number of specified factors plus one (the data at original resolution)
- `--reverse-array-attributes` reverses array attributes like `"resolution"` and `"offset"` that may be available in the source datasets
- `--output-container` specifies the path to the output n5 container
- `--container` specifies the path to the input container
  - `-d` adds a input dataset for conversion
    - `--target-dataset` sets the name of the output dataset
    - `--dataset-scale` sets the scale of this dataset, overriding the global `--scale` parameter
    - `--dataset-resolution` sets the resolution of the dataset

Paintera Conversion Helper builds upon [Apache Spark](https://spark.apache.org) and can be run on any Spark Cluster, which is particularly useful for large data sets.

### Paintera Data Format

[Previously](https://github.com/saalfeldlab/paintera/issues/61) we introduced a specification for the data format.
There are some ongoing discussions regarding the preferred data format. Paintera can accept any of a number of valid data and metadata formats
#### Data Containers
Through the N5 API, Paintera supports multiple data container types:
- N5
- HDF5
- Zarr
- N5/Zarr over AWS S3
- N5/Zarr over Google Cloud
#### Metadata
Paintera also can understand multiple metadata variants:
- Paintera Metadata [#61](https://github.com/saalfeldlab/paintera/issues/61)
- [Cellmap  Multiscale Metadata](https://github.com/janelia-cosem/schemas/blob/master/multiscale.md)
- [Deprecated n5-viewer metadata](https://github.com/janelia-cosem/schemas/blob/master/multiscale.md#deprecated-n5-viewer-style-source)
- [Current n5-viewer metadata](https://github.com/janelia-cosem/schemas/blob/master/multiscale.md#modern-n5-viewer-style-source)

#### Raw

Accept any of these:

1. any regular (i.e. default mode) three-dimensional N5 dataset that is integer or float. Optional attributes are `"resolution": [x,y,z]` and `"offset": [x,y,z]`.
2. any multiscale N5 group that has `"multiScale" : true` attribute and contains three-dimensional multi-scale datasets `s0` ... `sN`. Optional attributes are `"resolution": [x,y,z]` and `"offset: [x,y,z]"`. In addition to the requirements from (1), all `s1` ... `sN` datasets must contain `"downsamplingFactors": [x,y,z]` entry (`s0` is exempt, will default to `[1.0, 1.0, 1.0]`). All datasets must have same type. Optional attributes from (1) will be ignored.
3. (preferred) any N5 group with attribute `"painteraData : {"type" : "raw"}` and a dataset/group `data` that conforms with (2).

#### Labels

Accept any of these:

1. any regular (i.e. default mode) integer or varlength `LabelMultisetType` (`"isLabelMultiset": true`) three-dimensional N5 dataset. Required attributes are `"maxId": <id>`. Optional attributes are `"resolution": [x,y,z]`, `"offset": [x,y,z]`.
2. any multiscale N5 group that has `"multiScale" : true` attribute and contains three-dimensional multi-scale datasets `s0` ... `sN`. Required attributes are `"maxId": <id>`. Optional attributes are `"resolution": [x,y,z]`, `"offset": [x,y,z]`, `"maxId": <id>`. If `"maxId"` is not specified, it is determined at start-up and added (this can be expensive). In addition to the requirements from (1), all `s1` ... `sN` datasets must contain `"downsamplingFactors": [x,y,z]` entry (`s0` is exempt, will default to `[1.0, 1.0, 1.0]`). All datasets must have same type. Optional attributes from (1) will be ignored.
3. (preferred) any N5 group with attribute `"painteraData : {"type" : "label"}` and a dataset/group `data` that conforms with (2). Required attributes are `"maxId": <id>`. Optional sub-groups are:

- `fragment-segment-assignment` -- Dataset to store fragment-segment lookup table. Can be empty or will be initialized empty if it does not exist.
- `label-to-block-mapping`      -- Multiscale directory tree with one text files per id mapping ids to containing label: `label-to-block-mapping/s<scale-level>/<id>`. If not present, no meshes will be generated.
- `unique-labels`               -- Multiscale N5 group holding unique label lists per block. If not present (or not using `N5FS`), meshes will not be updated when commiting canvas.

#### Label Multisets

Paintera uses mipmap pyramids for efficient visualization of large data: At each level of the pyramid, the level of detail (and hence the amount of data) is less than at the previous level. This means that less data needs to be loaded and processed if a lower level detail suffices, e.g. if zoomed out far. Mipmap pyramids are created by gradually downsampling the data starting at original resolution. Naturally, some information is lost at each level. In a naive approach of *winner-takes-all* downsampling, voxels at lower resolution are assigned the most frequent label id of all contributing voxels at higher resolution, e.g.

```
 _____ _____
|  1  |  2  |
|-----+-----|
|  2  |  3  |
 ‾‾‾‾‾ ‾‾‾‾‾
```

will be summarized into
```
 ___________
|           |
|     2     |
|           |
 ‾‾‾‾‾‾‾‾‾‾‾
```
As a result, label representation does not degenerate gracefully. This becomes obvious in particular when generating 3D representations from such data:
![Winner-takes-all downsampling](img/multisets/mesh-winner-takes-all-cyan.png "Winner-takes-all downsampling")
Mehses generated at lower resolution exhibit discontinuities. Instead, we propose the use of a non-scalar representation of multi-scale label data: [label multisets](https://github.com/saalfeldlab/imglib2-label-multisets). A summary of all contained labels is stored at each individual voxel instead of a single label. Each voxel contains a list of label ids and the associated counts:

| Label | Count |
| ----- | ----- |
| 1     | 2     |
| 3     | 1     |
| 391   | 5     |

The list is sorted by label id to enable efficient containment checks through binary search for arbitrary labels at each voxel. Going back to the simple example for winner-takes-all downsampling,
```
 _____ _____
| 1:1 | 2:1 |
|-----+-----|
| 2:1 | 3:1 |
 ‾‾‾‾‾ ‾‾‾‾‾
```

will be summarized into
```
 ___________
|    1:1    |
|    2:2    |
|    3:1    |
 ‾‾‾‾‾‾‾‾‾‾‾
```
As a result, label data generates gracefully and without discontinuities in 3D representation:
![Label Multisets](img/multisets/mesh-multisets-magenta.png "Label multisets")
This becomes even more apparent when overlaying multiset downsampled labels (magenta) over winner-takes-all downsampled labels (cyan):
![Label Multisets over winner-takes-all](img/multisets/mesh-multisets-vertices-wta-solid-magenta-cyan.png "Label multisets over winner-takes-all downsampling")

These lists can get very large at lower resolution (in the most extreme case, all label ids of a dataset are listed in a single voxel) and it can become necessary to sacrifice some accuracy for efficiency for sufficiently large datasets. The [`-m N1 N2 ...` flag of the Paintera conversion helper](#supported-data) restricts the list of a label multiset to the `N` most frequent labels if `N>0`. In general, it is recommended to specify `N=-1` for the first few mipmap levels and then gradually decrease accuracy at lower resolution levels.

#### Extracting Scalar Label Data From Paintera Dataset

Introduced in version `0.7.0` of the [`paintera-conversion-helper`](https://github.com/saalfeldlab/paintera-conversion-helper), the
```
extract-to-scalar
```
command extracts the highest resolution scale level of a Paintera dataset as a scalar `uint64`. Dataset. This is useful for using Paintera painted labels (and assignments) in downstream processing, e.g. classifier training. Optionally, the `fragment-segment-assignment` can be considered and additional assignments can be added (versions `0.8.0` and later). See `extract-to-scalar --help` for more details. The `paintera-conversion-helper` is installed with Paintera when installed through [conda](#conda) or [pip](#pip) but may need to be updated to support this feature.


# Technical Notes

This section will expand in detail some of the technical aspect of some Paintera features that may be useful to understand.
#### 2D Viewer-Aligned Painting

In the current version of Paintera, all painting occurs in a temporary 2D Viewer aligned image. That is, annotations,
prior to being submitted to the canvas, exist in a temporary image that is parallel to the screen. The annotation is only
transformed to the label space upon submiting it to the canvas (e.g. releasing `SPACE` when painting, or pressing `ENTER` during _Shape Interpolation_
and _Segment Anything Automatic Labeling_). There are a few benefits of this:
- From an implementation standpoint, it is simpler, since you are operating on a 2D rectangular image, rather than transforming during painting
- You can annotate (temporarily) at higher resolution than the label data
  - When submitting to the canvas, this will of course only be at the resolution of the label data, but it can be used temporarily at higher resolution.
  - For example, Shape interpolation lets you interpolate at higher resolution, which helps remove voxel artifacts from the interpolation result.
- Resolved mask resolution related painting issue [#361](https://github.com/saalfeldlab/paintera/issues/361)
- Closer to a What You See Is What You Get (WYSIWYG) paint brush, especially at arbitrary rotations
  - The Viewer-Aligned mask is submitted to the canvas exactly as seen, though it must be fit to the label resolution
- Significant speedup of painting in slices of the data non-orthogonal to the label-space

Though there are benefits to painting in this way that is unrelated to the resolution of the underlying data, it can
also be costly, since you are now potentially painting at a higher resolution than necessary. There are a few strategies
to mitigate this cost. The main cost occurs when applying the higher resolution mask to the lower resolution (potentially)
label data. The Viewer mask is applied according to the following process:
1. Given the interval over the Viewer mask, transform that to label-space to get the label-aligned bounding box of the annotation
2. For each label coordinate in the bounding box, calculate its distance from the 2D viewer-aligned plane that was annotated on
    1. If the distance is farther than the maximum brush depth, then it's impossible that this position was annotated, so we are done
    2. If the distance is closer than resolution of a pixel in the viewer image, then we are so close that we must have been annotated
       1. Set the source value to the annotation label, and we are done
3. If label position is within the range, we need to iterate over the intersection of this label point and the viewer mask,
at the viewer mask resolution, to determine if any of the overlapping values where painted
    1. If any values in the viewer mask where painted, then fill the label with the painted value

This process lets Paintera determine the paint status of many label positions without ever checking if the Viewer mask was actually
painted or not. For the remaining values, it's only necessary to iterate over the 2D viewer mask until either a value is found, or it
is determined that the label point was not painted.

#### Shape Interpolation
Shape interpolation is a painting mode in Paintera that allows you to quickly annotate 3D volumes, in any orientation
based on key-point slices through the depth axis of the current view.

A rough overview of the shape-interpolation workflow is as follows:
1. Enter Shape interpolation mode
2. Paint (or fill, select, etc.) at the first desired slice
3. Scroll through to a different slice (also can pan and zoom)
4. Paint another slice
    - At this stage, the two painted sections will be "interpolated" through the intervening 3D space
    - More details [below](#interpolating-between-two-slices)
5. Repeat from step 3 for as many slices as you want
6. Persist the 3D interpolation to the canvas (ENTER) **OR** exit shape interpolation mode (ESC)

It's important to know that during shape interpolation, the 3D interpolated annotation is not persisted to either the
in-memory canvas, nor the underlying label dataset. This means that if you exit shape interpolation without accepting
the interpolated label, then it will be lost.

The powerful functionality of shape interpolation mode comes through its interactivity. While the basic workflow is to
paint individual slices and have the inerpolation form between them, shape interpolation benefits from modifying both
the slices as well as the results of the interpolation at some point between existing slices.

This lends itself to a workflow that speeds up annotation quite a bit, by being fast and loose with initial slice
labels, letting the interpolation fill the volumes in between, and then refining  the resultant interpolation.

###### Interpolating between two slices

The interpolation between any two given slices usually works as expected, but the implementation may lead to some
surprising results if you aren't aware of what's happening under the covers. Between every pair of slices, the interpolation
is calculated by the following:
1. Take each slice as 2D label
2. Calculate the distance transform over each slice respectively
3. Align the two slices such that they are "next to" each other in
    - i.e, combine them such that they make a 3D volumes with an interval of length `2` in the 3rd dimension, one slice at
index 0, the other at index 1
4. Interpolate over the two slices.
5. Scale the depth dimension back out so that they slices are separate the proper distance
6. Threshold over the now interpolated 3D volume between the two slices to get the resulting inteprolation

Because the interpolation is implemented this way, you tend to get smooth interpolations between overlapping slices, but
you may end up with unexpected results if the two slices don't overlap at all, or only just barely.


#### Automatic Labelling with Segment Anything
Paintera utilizes Meta's open-source [Segment Anything](https://segment-anything.com/) model to create automatic 2D segmentations over the current slice view.
The segmentations are produced by a two step process:
1. The currently active view is encoded into an image embedding that is later used to interactively generate segmentation proposals
    - This occurs only once per image, regardless of the number of hypotheses generated.  Generating this embedding is computationally demanding and benefits from a decent GPU (~30s per image on CPU, ~1.5s on GPU).  Similar to [Meta AI's browser demo](https://segment-anything.com/demo), we run a simple and free to use web-service to create these embeddings for you.
2. The embedding is used by a small and fast segmentation network to interactively create segmentation hypotheses based on user input (cursor movement, threshold , or inside/ outside points), this network runs on your local CPU
    - This occurs frequently and in real-time as you move your mouse, add control points, or adjust the threshold

To make the experience interactive, Paintera employs some tricks to make this features accessible for computers without powerful GPUs.

The images are compressed and sent to a small server with some GPUs.  This incurs some latency for the round-trip, but is much faster than encoding the image locally (unless an equivalent or better GPU is locally available).
Overall, the round-trip time from sending the image to receiving the embedding should be around 2-3 seconds.
  - The option to use your local GPU instead of an external service is also available, see [Running Prediction Service Locally](#running-prediction-service-locally).

Navigation is suspended while exploring segmentations so the same embedding can be re-used.

#### Generating a Segmentation
###### Real-time Predictions
As mentioned above, the predictions can be done very quickly even on CPU. However there are some limitations to be aware
about. The Segment anyting model normalizes the images during encoding to be `1024x1024`. This means that no matter the
resolution of our display, or the resolution of your data, the highest resolution prediction will be `1024x1024` per view.
The image that is sent to the model is only that of the current active view, at the highest screen-scale that is specified.
This means that it is likely the case that the view sent to the model is less than, or nearly `1024` along it's max dimension
anyway, so this effect may not even be noticeable when accepting a segmentation prediction.

An examples of the downscaling applied to an image on a 4K monitor with a `3840x2160` resolution:
- Fullscreen Paintera, with the default 2x2 grid, and side panel turned off
    - Each view will be `1920x1080`
    - Max dimensions is `1920`, so the image will be scaled down by roughly `50%`

Since Segment Anything operates only on `1024x1024` images, in cases like the above, not only will the image be
downscaled prior to sending it to the encoding service, the visual screen scale of the view will also temporarily be
set to match the resolution of the prediction image. This ensures that:
1. performance for the prediction is independant from screen size
2. refreshing the view is quicker, since it is only done at the reduced resolution of the prediction image

Importantly, in cases where the specified screen scale is already a more aggressive downscale that would be automatically
done as mentioned above, Paintera will use the lower-resolution screen scale. This ensures the prediction matches what
is displayed, but also allows you to determine whether you want a full-res (that is `1024x1024`) prediction, or a smaller,
but faster one.

###### Thresholding
When predicting over an image, the model returns an image of float values representing the probability that the given pixel
lies inside the desired segmentation. Using `Ctrl` + Scroll you can modify the threshold at which the segmentation is accepted.
This operates on the same prediction, such that modifying the threshold does not require re-predicting the segmentation
###### Connected Components
The resulting thresholded image is then filtered such that the resulting segmentation is a connected component. This helps
remove unwanted noisy edges of the prediction, that are not actually touching the segmentation under the cursor.
- When using `Ctrl` mode with include points, any connected component that contains an included point will be included
in the segmentation, even if the components themselves are not connected, or if they also contain an excluded point

###### Running Prediction Service Locally

If you want to run the service locally, follow the instruction at [JaneliaSciComp/SAM_service](https://github.com/JaneliaSciComp/SAM_service/tree/main).

Then, before launching Paintera, set the enviornmental variable `SAM_SERVICE_HOST` to the hostname or ip-address of the new service host.


<details>
<summary>Legacy (Deprecated)</summary>

### Installing Paintera

The following may or may not work with the current Paintera version. If using one of the following methods, verify the version of Paintera that is installed, as it likely is an old release.
<details>
<summary><b>Conda</b></summary>

#### Conda

Installation through conda requires an [installation of the conda package manager](https://docs.conda.io/projects/conda/en/latest/user-guide/install/).

Paintera installation requires an isolated conda environmet:
```sh
conda create --name paintera
conda activate paintera
```
Paintera is available for installation from the `conda-forge` channel:
```sh
conda install -c conda-forge paintera
```
For reasons that are not fully transparent to us at this time, conda may decide to install an outdated version of Paintera instead of the most recent one, you can fix this by updating Paintera:
```sh
conda update -c conda-forge paintera
```
Paintera can then be executed with the `paintera` command:
```
paintera [[JGO ARG... ][JVM ARG...] -- ][ARG...]
```
If you cannot see 3D rendering of objects or orthoslices, and Paintera floods the terminal with error messages like:
```sh
...
Feb 05, 2021 1:21:53 PM javafx.scene.shape.Mesh <init>
WARNING: System can't support ConditionalFeature.SCENE3D
...
```
please try to start it with forced GPU rendering:
```sh
paintera [JGO ARG... ][JVM ARG... ]-Dprism.forceGPU=true -- [ARG...]
```
</details>
<details>
<summary><b>Pip</b></summary>


#### Pip
#### Note: If installing via pip, it will be necessary to have Java and Maven installed manually.

Paintera is [available on the Python Package Index](https://pypi.org/project/paintera) and can be installed through pip ([`Python >= 3.6`](https://www.python.org) required):
```sh
pip install paintera
```
Generally, it is advisable to install packages into user space with pip, i.e.
```sh
pip install --user paintera
```
On Linux, the packages will be installed into `$HOME/.local` and Paintera will be located at
```sh
$HOME/.local/bin/paintera
```
You can add `$HOME/.local/bin` to the `PATH` to make the `paintera` accessible from different locations.
Paintera can then be executed with the `paintera` command:
```
paintera [[JGO ARG...] [JVM ARG...] --] [ARG...]
```

We recommend setting these JVM options:

| Option  | Description                                               |
|---------|-----------------------------------------------------------|
| -Xmx16G | Maximum Java heap space (replace 16G with desired amount) |
</details>
</details>
