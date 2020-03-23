#!/usr/bin/env kscript

@file:MavenRepository("scijava.public", "https://maven.scijava.org/content/groups/public")
@file:KotlinOpts("-classpath /usr/lib/jvm/java-8-openjdk/jre/lib/ext/jfxrt.jar")
@file:DependsOn("org.janelia.saalfeldlab:paintera:0.24.1-SNAPSHOT")

import picocli.AutoComplete

AutoComplete.main(
        "-n",
        "paintera",
        "org.janelia.saalfeldlab.paintera.PainteraCommandLineArgs",
        "--force")

