#!/usr/bin/env kscript

@file:DependsOn("org.json:json:20180813")

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class Change(val author: String, val message: String, val date: String) {
	constructor(commit: JSONObject) : this(
			author = (commit["author"] as JSONObject).getString("login"),
			message = (commit["commit"] as JSONObject).getString("message"),
			date = (((commit["commit"] as JSONObject)["author"] as JSONObject)).getString("date"))
}

val GITHUB_API_URL = "https://api.github.com";

fun getTags(repo: String) = JSONArray(fromURL(URL("$GITHUB_API_URL/repos/$repo/tags"))).filterIsInstance<JSONObject>()

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

val version = if (args.size > 0) args[0] else "0.18.0"
val commits = getCommitsBetweenReleases("saalfeldlab", "paintera", version, true)
println(commits)



