package com.dangerfield.drop2048.integration.feedback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the `feedback-triage` skill's Sentry queries against the enum that
 * produces the tag values it queries for.
 *
 * The failure this exists to catch is silent in both directions and visible in
 * neither. Rename `FeedbackKind.OwnerDirective`'s tag from `owner_directive` to
 * something tidier and the app keeps building, keeps sending, and Sentry keeps
 * accepting — but `feedback_kind:owner_directive` starts matching nothing. The
 * triage run reports "no new feedback" and is believed, because that is also
 * what a quiet week looks like. Nothing about a directive vanishing into a tag
 * nobody queries produces an error anywhere.
 *
 * So both ends are read as text out of the real artifacts: the enum source, and
 * the skill markdown a triage agent actually follows. Rename one without the
 * other and this fails.
 *
 * **What it proves.** That every `feedback_kind:<value>` the skill searches for
 * is a value the app can emit, and that every value the app can emit is searched
 * for. It does not prove the tag is set on the event — that is
 * `AppTelemetry.captureUserFeedback`'s job. A rename is the mistake people
 * actually make, and a rename is what this catches.
 *
 * It lives here because `:apps:integration` is the module whose tests are
 * allowed to read the repo off disk, and its build file is what supplies
 * `drop2048.repoRoot`.
 */
class FeedbackTriageQueryContractTest {

    @Test
    fun `every kind the app can emit is queried by the skill`() {
        val emitted = emittedTags()
        val queried = queriedTags()

        assertTrue(emitted.isNotEmpty(), "parsed no tags out of $KindSource; the reader is broken")
        assertTrue(queried.isNotEmpty(), "parsed no queries out of $Skill; the reader is broken")

        val unqueried = emitted - queried
        assertTrue(
            unqueried.isEmpty(),
            "The app can file feedback tagged ${unqueried.joinToString()} but the triage skill " +
                "never searches for it, so those reports are invisible to triage. Add the query " +
                "to $Skill.",
        )
    }

    @Test
    fun `the skill does not search for kinds the app cannot emit`() {
        val unmatchable = queriedTags() - emittedTags()

        assertTrue(
            unmatchable.isEmpty(),
            "The triage skill searches feedback_kind:${unmatchable.joinToString()} but no " +
                "FeedbackKind produces that tag, so the query silently returns nothing. Fix it " +
                "in $Skill or $KindSource.",
        )
    }

    @Test
    fun `both readers can actually fail`() {
        // The two tests above compare two parsed sets, and a reader that
        // silently returned nothing would make them pass. Prove each one finds a
        // value that is definitely present.
        assertTrue("owner_directive" in emittedTags(), "the enum reader found nothing recognisable")
        assertTrue("owner_directive" in queriedTags(), "the skill reader found nothing recognisable")
    }

    @Test
    fun `the skill points at the enum it is coupled to`() {
        // The table of fixed coordinates is how a triage agent finds the source
        // of truth when a query comes back empty. A stale path there sends it
        // looking in Sodogku.
        assertTrue(
            KindSource in read(Skill),
            "$Skill no longer names $KindSource, so nothing tells a triage run where the tag " +
                "values are defined.",
        )
    }

    @Test
    fun `the ledger exists, because without it triage re-files what it already filed`() {
        val ledger = File(repoRoot(), Ledger)

        assertTrue(ledger.isFile, "$Ledger is missing and the routine is no longer idempotent")
        assertEquals(
            true,
            ledger.readText().contains("## Entries"),
            "$Ledger has lost the heading the routine appends under",
        )
    }

    private fun emittedTags(): Set<String> =
        TagDeclaration.findAll(read(KindSource)).map { it.groupValues[1] }.toSet()

    private fun queriedTags(): Set<String> =
        SkillQuery.findAll(read(Skill)).map { it.groupValues[1] }.toSet()

    private fun read(relativePath: String): String {
        val file = File(repoRoot(), relativePath)
        require(file.isFile) { "$relativePath not found under ${repoRoot()}" }
        return file.readText()
    }

    private fun repoRoot(): String = System.getProperty("drop2048.repoRoot")
        ?: error("drop2048.repoRoot is unset. apps/integration/build.gradle.kts supplies it")

    private companion object {
        const val KindSource =
            "libraries/drop2048/src/commonMain/kotlin/com/dangerfield/drop2048/libraries/FeedbackKind.kt"
        const val Skill = ".claude/skills/feedback-triage/SKILL.md"
        const val Ledger = "docs/feedback-log.md"

        /** `tag = "owner_directive"` in an enum entry's constructor call. */
        val TagDeclaration = Regex("""tag\s*=\s*"([a-z_]+)"""")

        /** `feedback_kind:owner_directive` anywhere in the skill's prose or tables. */
        val SkillQuery = Regex("""feedback_kind:([a-z_]+)""")
    }
}
