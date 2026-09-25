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
copying both files into `src/content/legal/doublestack/`. That repo's
`auto-merge-legal.yml` merges the PR as soon as the site builds, and publishes.

**So editing these files is publishing them.** There is no click between your
commit and `nightjarlabs.llc`, give or take a couple of minutes. Write them as
if they are already live, because shortly they are.

The one gate is the build. The site validates frontmatter against a zod schema,
so a malformed file leaves the PR open and the live site serving the last good
version. It does not check whether the words are true. Nothing does. That part
is on you, which is the whole reason these files sit next to the code.

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
