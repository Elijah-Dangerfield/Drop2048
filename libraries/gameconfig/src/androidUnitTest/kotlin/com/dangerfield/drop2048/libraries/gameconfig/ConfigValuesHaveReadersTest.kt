package com.dangerfield.drop2048.libraries.gameconfig

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every remote config key has something that reads it.
 *
 * ## The failure this exists for
 *
 * `feature.leaderboards` shipped as a documented kill switch that killed
 * nothing. `LeaderboardsEnabled` was declared, carried a `description` promising
 * that off "hides the entry point", was covered by its own unit test, appeared
 * in the QA menu, and was editable in the admin console. It had **zero injection
 * sites**. Flipping it did nothing at all, and there was no way to find that out
 * short of flipping it on a build and watching the feature stay up.
 *
 * `pro.price.tier` was the same hole with a worse floor (D25). By the time
 * anyone read it, its default had drifted away from the real product id, so
 * wiring it as documented would have pointed the paywall at a product that does
 * not exist — and since `RealEntitlements` keys ownership on the same id and
 * writes `setPurchased(false)` on `NotOwned`, a remote change to it could have
 * revoked Pro from players who had paid. It was deleted rather than wired.
 *
 * Neither was visible to anything in the build. A `ConfiguredValue` subclass
 * with no consumer compiles cleanly, passes `MonetizationConfigValuesTest`
 * (which constructs the value directly and asserts its default, proving only
 * that the class works, never that anybody calls it), and resolves fine in the
 * DI graph, because the multibinding the QA menu enumerates does not care
 * whether anything else injects the member.
 *
 * ## Why this reads source rather than reflecting
 *
 * The question is "does anything inject this", and the runtime cannot answer it.
 * A kotlin-inject graph exposes what it can *provide*, not what asked; by the
 * time a test can see the `Set<QaConfigValue>` multibinding, an unread key and a
 * read one are identical objects. The injection site only exists in source, so
 * this reads source. That makes it a grep with a reason, and it is worth being
 * honest that it is: it can be defeated by a consumer written in a shape the
 * regex below does not know. It cannot be defeated by *forgetting*, which is the
 * failure that actually happened twice.
 *
 * It lives in `androidUnitTest` rather than `commonTest` because it needs a file
 * system, and in `:libraries:gameconfig` because that is where the doctrine that
 * caused this lives (see `MonetizationConfigValues`). It covers the whole repo,
 * not just this module.
 *
 * ## If this fails
 *
 * Do one of two things, and "add it to the ignore list" is not either of them:
 *
 * - **Wire it**, in the same change, to the behaviour its `description` claims.
 * - **Delete it**, and take the claim with it: the class, the admin console
 *   catalog entry in `apps/server/.../ConfigCatalog.kt`, and the row in SPEC 10's
 *   key table.
 */
class ConfigValuesHaveReadersTest {

    @Test
    fun `every config value is injected somewhere in production code`() {
        val sources = repoRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantPath().contains("/build/") }
            .map { it to it.readText().withoutComments() }
            .toList()

        assertTrue(
            sources.size > SanityFileFloor,
            "Only found ${sources.size} Kotlin files. This test walks the repo from " +
                "${repoRoot()}, and a wrong root makes it pass by finding nothing.",
        )

        val declarations = sources.flatMap { (file, code) ->
            DeclarationPattern.findAll(code).map { it.groupValues[1] to file }
        }

        assertTrue(
            declarations.size >= SanityValueFloor,
            "Only found ${declarations.size} ConfiguredValue declarations, expected at least " +
                "$SanityValueFloor. Either a lot of config was deleted, or the declaration " +
                "pattern stopped matching and this test is now watching nothing.",
        )

        val production = sources.filterNot { (file, _) -> file.isTest() || file.isQaSurface() }

        val unread = declarations.filter { (name, declaredIn) ->
            val consumer = ConsumerPattern(name)
            production.none { (file, code) -> file != declaredIn && consumer.containsMatchIn(code) }
        }

        if (unread.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("These config values are declared but nothing reads them:")
                    unread.forEach { (name, file) ->
                        appendLine("  - $name (${file.invariantPath().substringAfter("Drop2048/")})")
                    }
                    appendLine()
                    appendLine("A key with no reader is a promise the app does not keep. Wire it to")
                    appendLine("the behaviour its `description` claims, or delete it and remove the")
                    appendLine("claim from ConfigCatalog.kt and SPEC 10's key table. See D25.")
                },
            )
        }
    }


    /**
     * Comments are not code, in either direction, and this test got both wrong
     * without it.
     *
     * `JsonConfigValue`'s KDoc carries a worked example declaring a
     * `LevelLadderConfig` that does not exist, which the first run of this test
     * reported as an unread key. The mirror image is worse and would have been
     * silent: `OfflineFirstAppConfigRepository` names `[ConfigRefreshThrottleMs]`
     * twice in prose, so a key could have been counted as read on the strength
     * of a sentence describing it.
     *
     * `//` is only treated as a comment when nothing earlier on the line opened
     * a string, so a URL default survives. That is imprecise for a line holding
     * both a string and a trailing comment, and the imprecision can only drop
     * the tail of such a line, which is not a shape either pattern matches.
     */
    private fun String.withoutComments(): String = BlockComment.replace(this, "")
        .lineSequence()
        .map { line ->
            val slashes = line.indexOf("//")
            if (slashes >= 0 && !line.take(slashes).contains('"')) line.take(slashes) else line
        }
        .joinToString("\n")

    /**
     * The QA menu and the debug menu enumerate the whole `Set<QaConfigValue>`
     * multibinding rather than naming any value, so nothing in them can match
     * the pattern below today. They are excluded anyway, because the day one of
     * them does name a value, a debug screen reading a key is not the same thing
     * as the game honouring it.
     */
    private fun File.isQaSurface(): Boolean = invariantPath().let {
        it.contains("/features/debug/") || it.contains("/features/qatools/")
    }

    private fun File.isTest(): Boolean = invariantPath().let { path ->
        name.endsWith("Test.kt") || TestSourceSets.any { path.contains(it) }
    }

    private fun File.invariantPath(): String = path.replace('\\', '/')

    private fun repoRoot(): File {
        val start = requireNotNull(System.getProperty("user.dir")) { "No working directory" }
        var dir: File? = File(start).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("No settings.gradle.kts above $start")
    }

    private companion object {

        /**
         * Every `ConfiguredValue` in this codebase takes exactly one constructor
         * parameter and it is always spelled this way, which is what makes a
         * pattern viable at all. `abstract` is excluded because the two abstract
         * bases (`SpecialRatePerMille`, `SpecialFirstLevel`) are shapes rather
         * than keys, and their concrete subclasses are matched on their own.
         */
        val BlockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

        val DeclarationPattern =
            Regex("""(?<!abstract )\bclass (\w+)\(appConfigMap: AppConfigMap\)""")

        /**
         * Injected as a constructor parameter or property (`: Name,` / `: Name)`
         * / `: Name =`), or constructed directly, which is how
         * `ConfigRefreshThrottleMs` is read.
         *
         * Deliberately not a bare name match: `GameCatalog` has an unrelated
         * `private const val BoardRows`, and a test that counts that as a reader
         * of `board.rows` is a test that would have passed on the day
         * `feature.leaderboards` shipped dead.
         */
        fun ConsumerPattern(name: String) = Regex("""(:\s*$name\s*[,)=]|\b$name\()""")

        val TestSourceSets = listOf(
            "/commonTest/",
            "/androidUnitTest/",
            "/androidInstrumentedTest/",
            "/iosTest/",
            "/jvmTest/",
            "/jsTest/",
            "/src/test/",
            "/src/testFixtures/",
        )

        /** Guards against a wrong repo root making this vacuous. */
        const val SanityFileFloor = 300

        /** Guards against the declaration pattern silently matching nothing. */
        const val SanityValueFloor = 30
    }
}
