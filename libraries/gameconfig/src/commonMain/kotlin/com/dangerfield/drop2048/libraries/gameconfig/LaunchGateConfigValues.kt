package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.config.IntConfigValue
import com.dangerfield.drop2048.libraries.config.QaConfigValue
import com.dangerfield.drop2048.libraries.config.StringConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The launch gates' inputs (C11), and the two legal URLs beside them.
 *
 * **Every default here blocks nobody**, and that is the single most important
 * property in this file. These are the only keys in the project that can brick
 * every install at once, so a missing, malformed or unreachable config has to
 * resolve to letting the player play — SPEC 10's "the binary must be fully
 * playable with the server unreachable", at its sharpest. Zero is below no
 * version, `off` is not `blocking`, and `resolveLaunchGates` treats anything it
 * does not recognise as off.
 *
 * The admin console already warns on `upgrade.maintenanceMode` and
 * `upgrade.minSupportedVersionCode` (see `Support.dangerousWarning`), which were
 * written against these paths before the keys existed.
 */

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class MinSupportedVersionCode(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Force update: minimum supported build"
    override val description =
        "Builds below this are walled out and can only reach the store. 0 blocks nobody."
    override val path = "upgrade.minSupportedVersionCode"
    override val default = 0
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SoftUpdateVersionCode(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Soft update: suggested build"
    override val description =
        "Builds below this see a dismissible banner. 0 suggests nothing."
    override val path = "upgrade.softUpdateVersionCode"
    override val default = 0
}

/** One of `off`, `banner`, `blocking`. Anything else resolves to `off`. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class MaintenanceMode(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Maintenance mode"
    override val description = "off | banner | blocking. Blocking locks every player out."
    override val path = "upgrade.maintenanceMode"
    override val default = "off"
}

/**
 * The operator's own words, shown verbatim.
 *
 * Not a string resource, and it cannot be one: an incident message is written
 * during the incident. A blocking mode with this empty is treated as no gate at
 * all, because the message is the entire content of the screen.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class MaintenanceMessage(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Maintenance message"
    override val description = "Shown verbatim. A blocking mode with no message raises no gate."
    override val path = "upgrade.maintenanceMessage"
    override val default = ""
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalTermsVersion(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Terms version"
    override val description = "Raise when the terms change. A raise alone only shows a banner."
    override val path = "legal.termsVersion"
    override val default = 1
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalPrivacyVersion(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Privacy policy version"
    override val description = "Raise when the policy changes. A raise alone only shows a banner."
    override val path = "legal.privacyVersion"
    override val default = 1
}

/**
 * The floor below which acceptance must be re-taken before the game opens.
 *
 * Capped per document at the version actually on offer by `resolveLaunchGates`,
 * so a number above `termsVersion` cannot wall a player out of a gate the accept
 * button is unable to clear.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalForceReacceptBelow(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Force legal re-accept below"
    override val description = "Blocks until re-accepted. 0 blocks nobody."
    override val path = "legal.forceReacceptBelow"
    override val default = 0
}

/**
 * Defaults to the page published on the studio site. The text is written in
 * `legal/terms.md` in this repo and carried to `nightjarlabs.llc` by the Legal
 * Sync workflow; see `legal/README.md`.
 *
 * The default has to be a URL that actually resolves, because it is what a
 * build that cannot reach the config server opens from a gate the player has to
 * clear. An earlier default pointed at `drop2048.app`, a domain nobody had
 * bought, which made that gate a dead link.
 *
 * This URL is also filed with Apple and Google. Moving the page is a config
 * push rather than a release, but it is still two store re-filings, so treat
 * the slug as fixed.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalTermsUrl(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Terms URL"
    override val description = "Remote so a moved page is a config change, not a release."
    override val path = "legal.termsUrl"
    override val default = "https://nightjarlabs.llc/doublestack/terms"
}

/** The published privacy policy, for the reasons on [LegalTermsUrl]. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LegalPrivacyUrl(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Privacy policy URL"
    override val description = "Remote so a moved page is a config change, not a release."
    override val path = "legal.privacyUrl"
    override val default = "https://nightjarlabs.llc/doublestack/privacy"
}
