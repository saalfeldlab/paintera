#!/usr/bin/env kscript

@file:DependsOn("org.json:json:20180813")
@file:DependsOn("org.apache.maven:maven-model:3.6.1")
@file:DependsOn("org.eclipse.jgit:org.eclipse.jgit:5.4.0.201906121030-r")
@file:DependsOn("org.slf4j:slf4j-simple:1.7.25")

import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.HttpURLConnection
import java.net.URL

data class Change(val author: String, val message: String, val date: String) {
	constructor(commit: JSONObject) : this(
			author = (commit["author"] as JSONObject).getString("login"),
			message = (commit["commit"] as JSONObject).getString("message"),
			date = (((commit["commit"] as JSONObject)["author"] as JSONObject)).getString("date"))
}

fun versionFromString(str: String): Version {
        return if (str.contains("-")) {
                val split = str.split("-")
                val Mmp   = split[0].split(".").map { it.toInt() }.toIntArray()
                Version(Mmp[0], Mmp[1], Mmp[2], split[1])
        } else {
                val Mmp = str.split(".").map { it.toInt() }.toIntArray()
                Version(Mmp[0], Mmp[1], Mmp[2], null)
        }
}

data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String? = null): Comparable<Version> {

        val versionString = preRelease?.let { "$major.$minor.$patch-$preRelease" } ?: "$major.$minor.$patch"

        public constructor(params: Array<String>): this(
                params[0].toInt(),
                params[1].toInt(),
                params[2].toInt(),
                if (params.size == 4) params[3] else null)

        override fun toString() = versionString

        override fun compareTo(other: Version): Int {
                val majorComp = major.compareTo(other.major)
                if (majorComp != 0)
                        return majorComp
                val minorComp = minor.compareTo(other.minor)
                if (minorComp != 0)
                        return minorComp
                val patchComp = patch.compareTo(other.patch)
                if (patchComp != 0)
                        return patchComp
                if (preRelease === null && other.preRelease != null)
                        return 1
                if (other.preRelease === null && preRelease != null)
                        return -1
                else if (other.preRelease === null && preRelease === null)
                        return 0
                return preRelease!!.compareTo(other.preRelease!!)
        }
}

val GITHUB_API_URL = "https://api.github.com";

fun getTags(repo: String) = JSONArray(fromURL(URL("$GITHUB_API_URL/repos/$repo/tags"))).filterIsInstance<JSONObject>()

fun getCommitDate(
        repo: String,
        commit: String): String {
	val commitUrl = URL("$GITHUB_API_URL/repos/$repo/commits/$commit")
	val commitDate = JSONObject(fromURL(commitUrl)).getJSONObject("commit").getJSONObject("author").getString("date")
	return commitDate
}

fun getPreviousTag(tagName: String, tags: List<JSONObject>) = tags[tags.indexOfFirst { it.getString("name") == tagName } + 1]

fun getCommitTags(repo: String) = getTags(repo).filter { it.has("commit") }

fun getParentsFromTag(repo: String, tag: JSONObject) = JSONObject(fromURL(URL("$GITHUB_API_URL/repos/$repo/commits/${(tag["commit"] as JSONObject)["sha"]}")))["parents"] as JSONArray

fun getParentFromTag(repo: String, tag: JSONObject) = getParentsFromTag(repo, tag)
		.also { require(it.length() == 1) { "Release tags must have exactly one parent but got $it" } }[0] as JSONObject

fun getCommitsBetweenTags(
		repo: String,
		tagFrom: String,
		tagTo: String,
		useParentOfTagFrom: Boolean = true): List<Change> {
	val tagsObj = getTags(repo)
	val commitFrom = tagsObj.first { it["name"] == tagFrom }
	val commitTo = tagsObj.first { it["name"] == tagTo }
	val commitFromParent = getParentFromTag(repo, commitFrom)
	val shaTo = (commitTo["commit"] as JSONObject).getString("sha")
	val shaFrom = if (useParentOfTagFrom) commitFromParent.getString("sha") else (commitFrom["commit"] as JSONObject).getString("sha")
	return compare(repo, shaFrom, shaTo)
}

fun compare(repo: String, shaFrom: String, shaTo: String): List<Change> {
	val compareUrl = URL("$GITHUB_API_URL/repos/$repo/compare/$shaFrom...$shaTo")
	val compareObj = JSONObject(fromURL(compareUrl))
	val commits = compareObj["commits"] as JSONArray
	return commits.map { Change(it as JSONObject) }
}

fun getCommitsBetweenReleases(
		organization: String,
		name: String,
		versionTo: String,
		useParentOfTagFrom: Boolean = true): List<Change> {
	return getCommitsBetweenReleases(
			"$organization/$name",
			versionTo,
			useParentOfTagFrom = useParentOfTagFrom,
			versionToTag = { "$name-$it" })
}

fun getCommitsBetweenReleases(
		organization: String,
		name: String,
		versionFrom: String,
		versionTo: String,
		useParentOfTagFrom: Boolean = true): List<Change> {
	return getCommitsBetweenReleases(
			"$organization/$name",
			versionFrom,
			versionTo,
			useParentOfTagFrom = useParentOfTagFrom,
			versionToTag = { "$name-$it" })
}


fun getCommitsBetweenReleases(
		repo: String,
		versionTo: String,
		useParentOfTagFrom: Boolean = true,
		versionToTag: (String) -> String): List<Change> {
	return getCommitsBetweenTags(
			repo,
			getPreviousTag(versionToTag(versionTo), getCommitTags(repo)).getString("name"),
			versionToTag(versionTo),
			useParentOfTagFrom = useParentOfTagFrom)
}

fun getCommitsBetweenReleases(
		repo: String,
		versionFrom: String,
		versionTo: String,
		useParentOfTagFrom: Boolean = true,
		versionToTag: (String) -> String): List<Change> {
	return getCommitsBetweenTags(
			repo,
			versionToTag(versionFrom),
			versionToTag(versionTo),
			useParentOfTagFrom = useParentOfTagFrom)
}


fun fromURL(url: URL): String {
	println("Loading $url");
	val conn = url.openConnection() as HttpURLConnection;
	conn.setRequestMethod("GET");
	conn.setRequestProperty("Accept", "application/json");

	if (conn.getResponseCode() != 200)
		throw RuntimeException("Failed : HTTP error code : ${conn.responseCode}")

	val br = conn.inputStream.bufferedReader();
	val text = br.use(BufferedReader::readText)
	conn.disconnect()
	return text
}

val reader = MavenXpp3Reader()
val model  = FileReader("pom.xml").use { reader.read(it) }
val isSnapshot = model.version.contains("-SNAPSHOT")
val version = versionFromString(model.version)

val tags = getCommitTags("saalfeldlab/paintera").filter {
        try {
                versionFromString(it.getString("name").replace("paintera-", ""))
                true
        } catch (e: Exception) {
                false
        }
}

val repo = FileRepositoryBuilder().setGitDir(File(".git")).build()
val head = repo.resolve("HEAD")
val commitTo = if (isSnapshot) {
        head.name
} else {
        "paintera-${model.version}"
                        .takeUnless { t -> tags.count { it.getString("name") == t } == 0 }
                        ?: head.name
}

val tagFrom         = tags.first { versionFromString(it.getString("name").replace("paintera-", "")) < version }
val commitFrom      = getParentFromTag("saalfeldlab/paintera", tagFrom)
val relevantCommits = compare(
        repo    = "saalfeldlab/paintera",
        shaFrom = commitFrom.getString("sha"),
        shaTo   = commitTo)
// relevantCommits.forEach { println(it) }
val mergeCommits = relevantCommits.filter { it.message.startsWith("Merge pull request #") }

val breaking = mutableListOf<String>()
val features = mutableListOf<String>()
val fixes    = mutableListOf<String>()

for (commit in mergeCommits) {
        for (line in commit.message.lines()) {
                var l = line
                val isBreaking = if (l.contains("[BREAKING]")) {
                        l = l.replace("[BREAKING]", "").trim()
                        true
                } else false
                val isFeature = if (l.contains("[FEATURE]")) {
                        l = l.replace("[FEATURE]", "").trim()
                        true
                } else false
                val isBugfix = if (l.contains("[BUGFIX]")) {
                        l = l.replace("[BUGFIX]", "").trim()
                        true
                } else false
                if (isBreaking) breaking.add(l)
                if (isFeature)  features.add(l)
                if (isBugfix)   fixes.add(l)
        }
}

println(breaking)
println(features)
println(fixes)
// println("${model.version} is snapshot? $isSnapshot")
// println("$currentCommit ${getCommitDate("saalfeldlab/paintera", currentCommit)}")

