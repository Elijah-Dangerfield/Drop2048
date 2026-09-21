# Legal documents

`privacy.md` and `terms.md` are the **source of truth** for what Doublestack
publishes at:

- <https://nightjarlabs.llc/doublestack/privacy>
- <https://nightjarlabs.llc/doublestack/terms>

They live here, next to the code, because a privacy policy is a claim about what
a specific binary does. Keeping it in the marketing repo guarantees the day
comes when the app collects something the policy does not mention, and nobody
notices until a store reviewer does.

## How they get published

`.github/workflows/legal-sync.yml` watches `legal/**` on `main`. When either file
changes it opens a pull request against
[Elijah-Dangerfield/nightjar](https://github.com/Elijah-Dangerfield/nightjar),
copying both files into `src/content/legal/doublestack/`. Merging that PR builds
and deploys the site.

A pull request rather than a direct push, because these are legal documents and
a review step before publication is worth the extra click. **The consequence is
that an unmerged PR means the published policy is stale.** If you change these
files as part of a release, merging that PR is part of shipping the release, not
an afterthought — it's on `docs/store/release-checklist.md`.

## Rules

- **Frontmatter is a contract.** `app`, `title`, `updated` and `contact` are
  validated by a zod schema in the nightjar repo (`src/content.config.ts`).
  A missing or misspelled key fails that repo's build, not this one, so the
  failure shows up somewhere confusing. Bump `updated` whenever the text
  changes; both stores expect a policy to carry a date.
- **Do not edit the copies in the nightjar repo.** The next sync silently
  reverts them.
- **The URLs are filed with Apple and Google.** They are also the compiled
  defaults in `LaunchGateConfigValues.kt`. Changing the slug means re-filing on
  both stores and waiting out two reviews.
- **`privacy.md` and `docs/store/data-safety.md` are derived from the same
  facts.** When one changes, check the other, and check the two store labels it
  generates. A policy that disagrees with the nutrition label is worse than
  either alone.
