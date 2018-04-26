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
| `Space` left drag | Paint with id that was last toggled active (if any) |
| `Space` right drag | Erase within canvas only |
| `Shift` + `Space` right drag | Paint background label |
| `Space` wheel | change brush size |
| `F` | Flood-fill with id that was last toggled active (if any) |
| `N` | Select new, previously unused id |
| `Shift` + `C` | Commit current canvas into background |



