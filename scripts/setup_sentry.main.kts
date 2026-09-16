#!/usr/bin/env kotlin

/**
 * One-shot Sentry setup for a project generated from this template.
 *
 * Sentry ships off: `telemetry.properties` has a blank `sentry.dsn`, and blank
 * is a supported value meaning "reporting is disabled". Nothing warns, nothing
 * fails, and a project can sit like that for months. This script is the
 * on-ramp — it creates (or adopts) the Sentry project, writes the DSN into the
 * committed `telemetry.properties`, pushes the CI config, then sends a real
 * event and waits for it to show up, because the failure being fixed is
 * silence.
 *
 * Re-runnable. Everything it does is an upsert, so running it again repairs
 * drift instead of creating a second project.
 *
 * Run from the project root:
 *   ./scripts/setup_sentry.main.kts
 */

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.Properties
import kotlin.system.exitProcess

val SENTRY_API = "https://sentry.io/api/0"
val TELEMETRY_FILE = "telemetry.properties"
val VERSIONS_FILE = "versions.properties"
val POLL_ATTEMPTS = 30
val POLL_INTERVAL_MS = 4000L

// ── terminal ────────────────────────────────────────────────────────────────

fun green(text: String) = println("[32m$text[0m")
fun yellow(text: String) = println("[33m$text[0m")
fun red(text: String) = println("[31m$text[0m")
fun bold(text: String) = println("[1m$text[0m")

fun die(message: String): Nothing {
    red("✗ $message")
    exitProcess(1)
}

fun prompt(label: String, default: String? = null): String {
    val suffix = default?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
    while (true) {
        print("$label$suffix: ")
        System.out.flush()
        // Every prompt treats end-of-input as a hard stop. This script is
        // interactive by nature, and a piped/empty stdin must not spin.
        val input = (readlnOrNull() ?: die("No input on stdin — run this in a terminal.")).trim()
        if (input.isNotEmpty()) return input
        if (!default.isNullOrBlank()) return default
    }
}

/** Hides typed input when the JVM has a real console; says so when it can't. */
fun promptSecret(label: String): String {
    val console = System.console()
    while (true) {
        val value = if (console != null) {
            console.readPassword("$label: ")
                ?.let { String(it) }
                ?: die("No input on stdin — run this in a terminal.")
        } else {
            print("$label (input will be visible): ")
            System.out.flush()
            readlnOrNull() ?: die("No input on stdin — run this in a terminal.")
        }.trim()
        if (value.isNotEmpty()) return value
    }
}

fun confirm(label: String, default: Boolean = true): Boolean {
    val hint = if (default) "Y/n" else "y/N"
    print("$label ($hint): ")
    System.out.flush()
    return when (readlnOrNull()?.trim()?.lowercase()) {
        "y", "yes" -> true
        "n", "no" -> false
        else -> default
    }
}

// ── http ────────────────────────────────────────────────────────────────────

data class Response(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

fun request(
    method: String,
    url: String,
    token: String? = null,
    body: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
): Response {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 20_000
        readTimeout = 30_000
        token?.let { setRequestProperty("Authorization", "Bearer $it") }
        setRequestProperty("Accept", "application/json")
        extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
    }
    body?.let { connection.outputStream.use { stream -> stream.write(it.toByteArray()) } }
    val code = connection.responseCode
    val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
        ?.bufferedReader()?.use { it.readText() }.orEmpty()
    connection.disconnect()
    return Response(code, text)
}

/**
 * Enough JSON for this script's four fields. Sentry's payloads are large and
 * deeply nested; pulling one string key out of them by regex beats adding a
 * Maven dependency to a script whose whole point is that it runs immediately.
 */
fun stringValues(json: String, key: String): List<String> =
    Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"")
        .findAll(json)
        .map { it.groupValues[1] }
        .toList()

fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

// ── properties ──────────────────────────────────────────────────────────────

fun readProperty(file: File, key: String): String? {
    if (!file.exists()) return null
    val properties = Properties()
    file.inputStream().use(properties::load)
    return properties.getProperty(key)?.takeIf { it.isNotBlank() }
}

/**
 * Rewrites a key in place, keeping the file's comments and ordering. The
 * comments in `telemetry.properties` explain why the DSN is committed, which is
 * the line that stops someone "tidying" it back into local.properties.
 */
fun upsertProperty(file: File, key: String, value: String) {
    val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
    val index = lines.indexOfFirst { it.trimStart().startsWith("$key=") }
    if (index >= 0) lines[index] = "$key=$value" else lines += "$key=$value"
    file.writeText(lines.joinToString("\n").trimEnd() + "\n")
}

// ── sentry ──────────────────────────────────────────────────────────────────

fun explainTokenRequirement() {
    bold("\nSentry auth token")
    println(
        """
        This needs a USER token, not an organization token. They are not
        interchangeable and picking the wrong one is the usual way this fails:

          User token          Settings → Account → API → Auth Tokens
                              Scopes are selectable. Tick project:read,
                              project:write and org:read.

          Organization token  Settings → Organization Tokens (sntrys_…)
                              Carries exactly one scope, org:ci, and it is not
                              selectable. It answers 403 to every read endpoint,
                              so it cannot look up an org or read a DSN. It is
                              what CI wants, and this script will ask for it
                              separately at the end.

        Create one at https://sentry.io/settings/account/api/auth-tokens/
        It is used for this run only and never written anywhere.
        """.trimIndent()
    )
    println()
}

fun resolveOrg(token: String, suggested: String?): String {
    val response = request("GET", "$SENTRY_API/organizations/", token)
    if (response.code == 401) die("Sentry rejected that token (401). Create a new user token and re-run.")
    if (response.code == 403) {
        die(
            "That token answered 403 to /organizations/, which is what an organization " +
                "(sntrys_…) token does. Create a USER token with org:read instead."
        )
    }
    if (!response.isSuccess) die("Could not list organizations (HTTP ${response.code}): ${response.body.take(300)}")

    val slugs = stringValues(response.body, "slug").distinct()
    if (slugs.isEmpty()) die("That token can see no organizations. Check it has org:read.")
    if (slugs.size > 1) println("Organizations visible to this token: ${slugs.joinToString(", ")}")

    val default = suggested?.takeIf { it in slugs } ?: slugs.first()
    val chosen = prompt("Sentry org slug", default)
    if (chosen !in slugs) {
        val check = request("GET", "$SENTRY_API/organizations/$chosen/", token)
        if (!check.isSuccess) die("Org '$chosen' is not readable with this token (HTTP ${check.code}).")
    }
    return chosen
}

fun firstTeam(token: String, org: String): String {
    val response = request("GET", "$SENTRY_API/organizations/$org/teams/", token)
    if (!response.isSuccess) die("Could not list teams in '$org' (HTTP ${response.code}).")
    val slugs = stringValues(response.body, "slug").distinct()
    if (slugs.isEmpty()) die("Org '$org' has no teams. Create one in Sentry, then re-run.")
    if (slugs.size == 1) return slugs.first()
    println("Teams in $org: ${slugs.joinToString(", ")}")
    return prompt("Team to own the project", slugs.first())
}

fun ensureProject(token: String, org: String, project: String): Boolean {
    val existing = request("GET", "$SENTRY_API/projects/$org/$project/", token)
    if (existing.isSuccess) {
        green("✓ Adopted existing Sentry project $org/$project")
        return false
    }
    if (existing.code != 404) {
        die("Unexpected response looking up $org/$project (HTTP ${existing.code}): ${existing.body.take(300)}")
    }

    val team = firstTeam(token, org)
    val payload = """{"name":"$project","slug":"$project","platform":"android"}"""
    val created = request("POST", "$SENTRY_API/teams/$org/$team/projects/", token, payload)
    if (created.code == 403) {
        die("Creating the project was refused (403). The token needs project:write (a user token scope).")
    }
    if (!created.isSuccess) {
        die("Could not create $org/$project (HTTP ${created.code}): ${created.body.take(300)}")
    }
    green("✓ Created Sentry project $org/$project under team $team")
    return true
}

fun readDsn(token: String, org: String, project: String): String {
    val response = request("GET", "$SENTRY_API/projects/$org/$project/keys/", token)
    if (!response.isSuccess) die("Could not read client keys for $org/$project (HTTP ${response.code}).")
    return stringValues(response.body, "public")
        .firstOrNull { it.startsWith("https://") }
        ?: die("$org/$project has no enabled client key. Add one in Project Settings → Client Keys.")
}

data class Dsn(val publicKey: String, val host: String, val projectId: String)

fun parseDsn(dsn: String): Dsn {
    val match = Regex("^https://([^@]+)@([^/]+)/(.+)$").find(dsn.trim())
        ?: die("DSN is not in the expected https://<key>@<host>/<id> shape: $dsn")
    val (key, host, id) = match.destructured
    return Dsn(key, host, id)
}

/**
 * Posts straight to the DSN's store endpoint rather than shelling out to
 * sentry-cli. One less thing to install, and it exercises the DSN exactly as
 * the app will: if this is accepted, the value written to telemetry.properties
 * is live.
 */
fun sendTestEvent(dsn: Dsn, marker: String): Boolean {
    val eventId = java.util.UUID.randomUUID().toString().replace("-", "")
    val payload = """
        {"event_id":"$eventId","timestamp":"${Instant.now()}","platform":"other",
         "level":"info","logger":"setup_sentry","environment":"setup-check",
         "message":{"formatted":"$marker"},"tags":{"setup_check":"$marker"}}
    """.trimIndent().replace("\n", "")

    val response = request(
        method = "POST",
        url = "https://${dsn.host}/api/${dsn.projectId}/store/",
        body = payload,
        extraHeaders = mapOf(
            "X-Sentry-Auth" to "Sentry sentry_version=7, sentry_client=setup-sentry/1.0, sentry_key=${dsn.publicKey}"
        ),
    )
    if (!response.isSuccess) {
        red("✗ Sentry rejected the test event (HTTP ${response.code}): ${response.body.take(300)}")
        return false
    }
    green("✓ Test event accepted by ingest (id $eventId)")
    return true
}

fun awaitEvent(token: String, org: String, project: String, marker: String): Boolean {
    print("Waiting for the event to appear in $org/$project")
    repeat(POLL_ATTEMPTS) {
        val url = "$SENTRY_API/projects/$org/$project/issues/" +
            "?query=${encode("setup_check:$marker")}&statsPeriod=1h"
        val response = request("GET", url, token)
        // A tag search that matched returns a populated array; a miss returns
        // literally "[]", so length is the whole test.
        if (response.isSuccess && response.body.trim().length > 2) {
            println()
            return true
        }
        print(".")
        System.out.flush()
        Thread.sleep(POLL_INTERVAL_MS)
    }
    println()
    return false
}

// ── ci ──────────────────────────────────────────────────────────────────────

fun hasGhRepo(): Boolean = try {
    ProcessBuilder("gh", "repo", "view", "--json", "name")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor() == 0
} catch (_: Exception) {
    false
}

fun runGh(vararg args: String, stdin: String? = null): Boolean = try {
    val process = ProcessBuilder(listOf("gh") + args)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    if (stdin != null) process.outputStream.use { it.write(stdin.toByteArray()) } else process.outputStream.close()
    process.waitFor() == 0
} catch (_: Exception) {
    false
}

fun printManualCiCommands(org: String, project: String) {
    yellow("\nSet these yourself once the repo exists (paste as-is; it prompts you):")
    println(
        """
        stty -echo; printf "Paste the Sentry org token (sntrys_...): "; read -r T; stty echo; printf "\n"
        gh secret set SENTRY_AUTH_TOKEN --body "${'$'}T" && unset T
        gh variable set SENTRY_ORG --body "$org"
        gh variable set SENTRY_PROJECT --body "$project"
        """.trimIndent()
    )
}

fun configureCi(org: String, project: String) {
    bold("\nCI configuration")
    if (!hasGhRepo()) {
        yellow("No GitHub repo reachable via gh (not installed, not authenticated, or no remote yet).")
        printManualCiCommands(org, project)
        return
    }

    val orgOk = runGh("variable", "set", "SENTRY_ORG", "--body", org)
    val projectOk = runGh("variable", "set", "SENTRY_PROJECT", "--body", project)
    if (orgOk && projectOk) {
        green("✓ Set repo variables SENTRY_ORG=$org, SENTRY_PROJECT=$project")
    } else {
        yellow("Could not set repo variables — set SENTRY_ORG / SENTRY_PROJECT by hand.")
    }

    println(
        "\nCI uploads mappings and dSYMs with an ORGANIZATION token (sntrys_…), " +
            "not the user token above. One org token covers every project you generate, " +
            "so reuse the one you already have."
    )
    if (!confirm("Set the SENTRY_AUTH_TOKEN repo secret now?", default = true)) {
        yellow("Skipped. Release builds will still ship; mappings just won't upload.")
        printManualCiCommands(org, project)
        return
    }
    val ciToken = promptSecret("Sentry organization token (sntrys_…)")
    if (!ciToken.startsWith("sntrys_")) {
        yellow("That does not look like an organization token. Setting it anyway — CI needs org:ci.")
    }
    if (runGh("secret", "set", "SENTRY_AUTH_TOKEN", stdin = ciToken)) {
        green("✓ Set repo secret SENTRY_AUTH_TOKEN")
    } else {
        yellow("Could not set the secret.")
        printManualCiCommands(org, project)
    }
}

// ── main ────────────────────────────────────────────────────────────────────

val root = File(".").canonicalFile
val telemetryFile = File(root, TELEMETRY_FILE)
if (!telemetryFile.exists()) {
    die("$TELEMETRY_FILE not found. Run this from the project root.")
}

bold("\n━━ Sentry setup ━━")
println("Creates or adopts a Sentry project, commits its DSN, wires CI, and proves an event arrives.")

explainTokenRequirement()
val userToken = promptSecret("Sentry user auth token")

val knownOrg = readProperty(telemetryFile, "sentry.org")
val org = resolveOrg(userToken, knownOrg)

// Derived from the app id rather than the directory name, so the Sentry slug
// and the installed package stay recognisably the same app. Only the last two
// segments: the reverse-DNS prefix is the same for every app you ship, so
// including it makes every slug start identically. Sentry slugs are
// lowercase-alphanumeric-and-hyphen.
val applicationId = readProperty(File(root, VERSIONS_FILE), "applicationId")
val defaultProject = readProperty(telemetryFile, "sentry.project")
    ?: applicationId?.split('.')?.filter { it.isNotBlank() }?.takeLast(2)
        ?.joinToString("-")?.lowercase()?.replace(Regex("[^a-z0-9-]"), "-")
    ?: root.name.lowercase()
val project = prompt("Sentry project slug", defaultProject)

ensureProject(userToken, org, project)
val dsn = readDsn(userToken, org, project)
val parsedDsn = parseDsn(dsn)

upsertProperty(telemetryFile, "sentry.dsn", dsn)
upsertProperty(telemetryFile, "sentry.org", org)
upsertProperty(telemetryFile, "sentry.project", project)
green("✓ Wrote the DSN into $TELEMETRY_FILE — commit it, so a fresh clone reports with no local setup")

if (readProperty(File(root, "local.properties"), "sentry.dsn") != null) {
    yellow(
        "\nlocal.properties still sets sentry.dsn, which overrides the committed value " +
            "for you but for nobody else. Delete that line unless you mean to point " +
            "your own builds somewhere different."
    )
}

configureCi(org, project)

bold("\nProving it works")
val marker = "setupcheck" + java.util.UUID.randomUUID().toString().take(8).replace("-", "")
val delivered = sendTestEvent(parsedDsn, marker) && awaitEvent(userToken, org, project, marker)

println()
if (delivered) {
    green("━━ Sentry is live ━━")
    println("A test event reached $org/$project and was found by search.")
    println("https://sentry.io/organizations/$org/issues/?project=&query=${encode("setup_check:$marker")}")
    println()
    println("Next: commit $TELEMETRY_FILE, then rebuild. Debug builds report from the next launch.")
} else {
    red("━━ Setup did NOT finish ━━")
    println(
        """
        The DSN is written, but no event came back. Do not assume it works.
        Common causes, in the order worth checking:
          - the project has inbound filters or a spike-protection quota on it
          - the event is still in the ingest queue (rare, but wait a minute and
            search Sentry for setup_check:$marker by hand)
          - the org/project pair is not the one the DSN belongs to
        """.trimIndent()
    )
    exitProcess(1)
}
