# App icon and store artwork

Audited 2026-09-10 for C13. Nothing here was changed; the icon is owner-blocked
(`OWNER-TODO.md`, "Art"). This exists so the brief is specific about what has to be delivered and
where each file goes.

## 1. Summary: every icon surface in the project is the template placeholder

| Surface | File | State | Blocking? |
|---|---|---|---|
| Android launcher, flat | `res/mipmap-*/ic_launcher.webp`, five densities | Grey rounded square reading **"YOUR APPS IMAGE HERE"** | **Yes** |
| Android launcher, round | `res/mipmap-*/ic_launcher_round.webp` | Same | **Yes** |
| Android adaptive foreground | `res/mipmap-*/ic_launcher_foreground.webp` + `drawable-v24/ic_launcher_foreground.xml` | Same | **Yes** |
| Android adaptive background | `drawable/ic_launcher_background.xml` + `values/ic_launcher_background.xml` | Template colour | **Yes** |
| Android themed (monochrome) | none | **Absent.** No `<monochrome>` element in either `mipmap-anydpi-v26` XML, so Android 13+ themed icons fall back to the standard icon | No, cosmetic |
| Android notification icon | none | Not present, and not needed: the app posts no notifications | No |
| Play listing icon, 512x512 | `apps/compose/src/androidMain/ic_launcher-playstore.png` | Placeholder, correct size | **Yes** |
| Play feature graphic, 1024x500 | none | Does not exist | **Yes**, Play requires one |
| iOS app icon | `Assets.xcassets/AppIcon.appiconset/icon_template.png` | Placeholder, 1024x1024, correct size | **Yes**, App Store review rejects placeholder icons |
| iOS launch screen | Generated (`INFOPLIST_KEY_UILaunchScreen_Generation = YES`) | Plain, no art | No |
| Web pages icon | `pages/app-icon.png` (1024), `apple-touch-icon.png` (180), `favicon.png` (64) | Placeholder, correct sizes | No, but it is public |

**Nothing about this is a code problem.** Every file is the right size in the right place and is
waiting for art. Replacing them is a file swap, and once the 1024 master exists the Android
densities can be generated from it in Android Studio's Image Asset tool.

## 2. Two constraints on the art that come from elsewhere in this chunk

1. **It must not read as a children's app.** `age-rating.md` §2 Branch A: a 13+ target-audience
   declaration next to preschool-looking artwork is how a Play review flag happens. Bright is fine,
   toybox is not. This is a real constraint and it is cheaper to hear now than after a rejection.
2. **It carries the name decision.** The Play listing icon and the App Store icon are the two
   places a wordmark would live, and the name is not decided (`listing.md`). An icon that spells
   out "Drop 2048" is a file that has to be redrawn if the name moves.

## 3. Sizes needed, so the brief is one list

| Deliverable | Size | For |
|---|---|---|
| Master icon | 1024x1024 PNG, no alpha, no rounded corners | Everything below is cut from it |
| iOS app icon | 1024x1024, square, **no transparency** (App Store rejects alpha) | `icon_template.png`, replace in place |
| Play listing icon | 512x512 PNG, 32-bit with alpha | `ic_launcher-playstore.png` |
| Android adaptive foreground | 108x108 dp safe zone, art inside the centre 66dp circle | Generated from the master |
| Android monochrome | Single-colour silhouette | Optional, Android 13+ themed icons |
| Play feature graphic | 1024x500 | Play listing header. Not the icon; a separate composition |
| Launch screen art | Optional | The generated iOS launch screen is plain and acceptable |
