package com.dangerfield.drop2048.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Registers the project's custom rule set. Discovered by detekt via the
 * `META-INF/services/dev.detekt.api.RuleSetProvider` entry. The rule set id
 * (`drop2048`) namespaces the rules in `detekt.yml`.
 */
class Drop2048RuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("drop2048")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ::VerifyStrings,
            ::AnimatedStateReadInComposition,
        ),
    )
}
