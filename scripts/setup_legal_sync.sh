#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Sets up the two secrets the Legal Sync pipeline needs. Run it once.
#
#   1. FIREBASE_SERVICE_ACCOUNT on the website repo, so merging a sync PR
#      deploys the site. Created here: a dedicated service account, a key, and
#      the GitHub secret.
#   2. NIGHTJAR_SITE_TOKEN on this repo, so this repo can open a PR against the
#      website repo. GitHub has no API for minting a personal access token, so
#      this one you create in the browser and paste in.
#
# Safe to re-run. The service account is reused if it already exists, and a new
# key is minted each time (old keys stay valid — delete them in the console if
# you want them gone).
#
# Needs: gh (logged in), gcloud (logged in as a project owner).
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

APP_REPO="Elijah-Dangerfield/Drop2048"
SITE_REPO="Elijah-Dangerfield/nightjar"
GCP_PROJECT="nightjarlabs-db51f"
SA_NAME="github-action-legal-sync"
SA_EMAIL="${SA_NAME}@${GCP_PROJECT}.iam.gserviceaccount.com"

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
step() { printf "\n\033[1;36m==> %s\033[0m\n" "$1"; }
ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; }

# ── Preflight ────────────────────────────────────────────────────────────────
step "Checking tools"
for cmd in gh gcloud; do
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "  ✗ $cmd is not installed. brew install $cmd" >&2; exit 1; }
  ok "$cmd"
done

gh auth status >/dev/null 2>&1 || {
  echo "  ✗ gh is not logged in. Run: gh auth login" >&2; exit 1; }
ok "gh is logged in"

gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q . || {
  echo "  ✗ gcloud has no active account. Run: gcloud auth login" >&2; exit 1; }
ok "gcloud is logged in as $(gcloud auth list --filter=status:ACTIVE --format='value(account)' | head -1)"

# ── 1. Firebase deploy credentials ───────────────────────────────────────────
step "Firebase service account for $SITE_REPO"

gcloud services enable \
  firebasehosting.googleapis.com iam.googleapis.com iamcredentials.googleapis.com \
  --project "$GCP_PROJECT" --quiet
ok "APIs enabled"

if gcloud iam service-accounts describe "$SA_EMAIL" --project "$GCP_PROJECT" >/dev/null 2>&1; then
  ok "service account already exists: $SA_EMAIL"
else
  gcloud iam service-accounts create "$SA_NAME" \
    --project "$GCP_PROJECT" \
    --display-name "GitHub Actions: deploy nightjarlabs.llc" \
    --quiet
  ok "created $SA_EMAIL"
fi

# firebasehosting.admin publishes releases; firebase.viewer lets the action
# resolve the site id. These are the same two roles `firebase init
# hosting:github` grants.
for role in roles/firebasehosting.admin roles/firebase.viewer; do
  gcloud projects add-iam-policy-binding "$GCP_PROJECT" \
    --member "serviceAccount:${SA_EMAIL}" --role "$role" \
    --condition=None --quiet >/dev/null
  ok "granted $role"
done

# The key never touches the repo and never lands in a world-readable temp file.
KEY_FILE="$(umask 077 && mktemp -t fb-sa-key)"
cleanup() { rm -f "$KEY_FILE"; }
trap cleanup EXIT

gcloud iam service-accounts keys create "$KEY_FILE" \
  --iam-account "$SA_EMAIL" --project "$GCP_PROJECT" --quiet
ok "minted a key"

gh secret set FIREBASE_SERVICE_ACCOUNT --repo "$SITE_REPO" < "$KEY_FILE"
ok "set FIREBASE_SERVICE_ACCOUNT on $SITE_REPO"

rm -f "$KEY_FILE"
trap - EXIT

# ── 2. Cross-repo pull request token ─────────────────────────────────────────
step "Pull request token for $APP_REPO"

if gh secret list --repo "$APP_REPO" | grep -q '^NIGHTJAR_SITE_TOKEN'; then
  bold "  NIGHTJAR_SITE_TOKEN already exists on $APP_REPO."
  printf "  Replace it? (y/N): "
  read -r replace
  case "$replace" in [yY]*) ;; *) ok "kept the existing token"; replace=no ;; esac
else
  replace=yes
fi

if [ "$replace" != "no" ]; then
  cat <<EOF

  Create a fine-grained personal access token:

    https://github.com/settings/personal-access-tokens/new

    Resource owner    Elijah-Dangerfield
    Repository access Only select repositories → nightjar
    Permissions       Contents      → Read and write
                      Pull requests → Read and write
    Expiration        Your call. When it expires, Legal Sync fails loudly on
                      the next push to legal/ — it does not fail silently.

  Nothing else. A token with more access than this buys you nothing.

EOF
  stty -echo
  printf "  Paste the token (input hidden): "
  read -r TOKEN
  stty echo
  printf "\n"

  if [ -z "$TOKEN" ]; then
    echo "  ✗ No token entered. Nothing was set." >&2
    exit 1
  fi
  if [ "${TOKEN#github_pat_}" = "$TOKEN" ]; then
    echo "  ✗ That does not look like a fine-grained token (expected a github_pat_ prefix)." >&2
    echo "    A classic 'ghp_' token works too, but grants far more than this needs." >&2
    exit 1
  fi

  printf '%s' "$TOKEN" | gh secret set NIGHTJAR_SITE_TOKEN --repo "$APP_REPO"
  unset TOKEN
  ok "set NIGHTJAR_SITE_TOKEN on $APP_REPO"
fi

# ── Done ─────────────────────────────────────────────────────────────────────
step "Verifying"
gh secret list --repo "$SITE_REPO" | sed 's/^/  /'
gh secret list --repo "$APP_REPO" | grep NIGHTJAR_SITE_TOKEN | sed 's/^/  /'

cat <<EOF

$(bold "Done.")

Try it end to end:

  gh workflow run legal-sync.yml --repo $APP_REPO

That opens a PR on $SITE_REPO. Merging it deploys
https://nightjarlabs.llc/doublestack/privacy and /terms.

EOF
