# Export compliance

`apps/ios/iosApp/Info.plist` declares `ITSAppUsesNonExemptEncryption = false`.
That is what stops App Store Connect asking about encryption on every build.

## Why false is correct today

Apple's question is about encryption the app *implements*. This one implements
none.

- The only `CacheEncryption` is `NoEncryption`, which returns its input
  unchanged (`libraries/storage/.../CacheJsonSerializer.kt`). Nothing else
  implements that interface.
- No `CryptoKit`, `CommonCrypto` or equivalent anywhere in the Kotlin or Swift.
- The three iOS dependencies (`sentry-cocoa`, `swift-package-manager-google-mobile-ads`,
  `swift-package-manager-google-user-messaging-platform`) use `NSURLSession` and
  the system Security framework rather than shipping their own TLS.

The app does speak HTTPS, to the config server, Sentry, Grafana and AdMob. That
is the encryption *within Apple's operating system*, which is exempt and is
exactly what the form's second option excludes. Answering that second option
instead would pull a puzzle game into a CCATS filing and an annual
self-classification report for no reason.

## What would make it a lie

The declaration is signed on every build from here on, and nothing in CI checks
it. Revisit this file if any of these happens:

- The local cache gets encrypted, meaning something other than `NoEncryption`
  starts implementing `CacheEncryption`.
- Any dependency starts carrying its own crypto rather than calling Apple's.
  Firebase and anything gRPC-based bundle BoringSSL, so adding one is the
  likeliest way this changes.
- The app gains end-to-end encryption, its own key exchange, or encrypted
  backups.

A false declaration on an export form is not a paperwork problem, so the cost of
being wrong here is not symmetric with the cost of checking.

## If it does change

Remove the key from `Info.plist` and answer the App Store Connect prompt by
hand, or set it to `true` and complete the export compliance documentation
Apple then asks for. Which of the two depends on whether the encryption is
exempt, and that is a question for someone who can read the EAR category 5 part
2 notes, not a guess.
