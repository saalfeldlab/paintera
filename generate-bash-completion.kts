#!/usr/bin/env kscript

@file:MavenRepository("scijava.public", "https://maven.scijava.org/content/groups/public")
@file:KotlinOpts("-classpath /usr/lib/jvm/java-8-openjdk/jre/lib/ext/jfxrt.jar")
@file:DependsOn("org.janelia.saalfeldlab:paintera:0.18.1-SNAPSHOT")
@file:DependsOn("org.slf4j:slf4j-simple:1.7.25")

import picocli.AutoComplete

AutoComplete.main(
        "-n",
        "paintera",
        "org.janelia.saalfeldlab.paintera.PainteraCommandLineArgs",
        "--force")

