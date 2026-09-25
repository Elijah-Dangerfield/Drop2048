# Changelog

## [0.2.0](https://github.com/Elijah-Dangerfield/Drop2048/compare/v0.1.0...v0.2.0) (2026-09-25)


### ⚠ BREAKING CHANGES

* The Daily Challenge is removed, along with the GameMode discriminator, the daily_result table and the SevenDays and ThirtyDays achievements. PINNED_DIGEST moves a fourth time and SAVE_FORMAT_VERSION goes to 8, so runs saved under the old rules do not resume. Nothing is banked yet: there are no store accounts, no live board and no shipped build.
* **game:** `Input.Nudge`, `EngineConfig.nudgeRows` and `SpeedCurve.softDropMsPerRow` are removed, along with the `speed.nudgeRows` and `speed.softDropMsPerRow` remote keys. Saved runs from earlier builds are refused.

### Features

* **achievements:** add badges, platform leaderboards and sharing, with their call sites ([5fee174](https://github.com/Elijah-Dangerfield/Drop2048/commit/5fee174fd2065359960926eb0433033dad0dff9a))
* add :libraries:cascade, the Drop 2048 game engine ([96e6b92](https://github.com/Elijah-Dangerfield/Drop2048/commit/96e6b9253af45f24768bda55b708e8d3d8a1ddbe))
* **ads:** a house ad network, QA config overrides, and ad tools in the debug menu ([81207c6](https://github.com/Elijah-Dangerfield/Drop2048/commit/81207c6d7202f36e646fa492ef1832a6a087e362))
* **ads:** pick live units from the release channel, not a hand flip ([dabcf3d](https://github.com/Elijah-Dangerfield/Drop2048/commit/dabcf3dd76aa632c2d57b2a6c98f6560276038b9))
* **ads:** record the real AdMob apps and ad units ([a8d2a7c](https://github.com/Elijah-Dangerfield/Drop2048/commit/a8d2a7c6452e1368e82d8ff69d343ae271b3d3a1))
* **ads:** SPEC 12 monetization — rewarded continue, Pro, and gated interstitials ([d8035c7](https://github.com/Elijah-Dangerfield/Drop2048/commit/d8035c7e16a3aac4b5e29d162e2cdde02484b8a0))
* **ads:** wire AdMob on iOS, ported from Sodogku ([9e7cd46](https://github.com/Elijah-Dangerfield/Drop2048/commit/9e7cd46ab5905e033052940aed87c701ec8a71dd))
* **balance:** put the drop clock in the loop and re-cut the opening speed curve ([321070a](https://github.com/Elijah-Dangerfield/Drop2048/commit/321070ab65317fbac9854b845f1fbbcc231cc09c))
* **billing:** wire StoreKit 2 on iOS, ported from Sodogku ([5376c78](https://github.com/Elijah-Dangerfield/Drop2048/commit/5376c78707aeac6e20936112fde3ded3431e7463))
* block palettes, cue pairing and board geometry for the design system ([e6e95b3](https://github.com/Elijah-Dangerfield/Drop2048/commit/e6e95b3dd8b6196f0530717e0053055fdc702ffb))
* **config:** wire every SPEC 10 key through :libraries:config ([96f7c40](https://github.com/Elijah-Dangerfield/Drop2048/commit/96f7c40599c556f48243555adec86f24c27b60b9))
* **daily:** add the Daily Challenge ([c070289](https://github.com/Elijah-Dangerfield/Drop2048/commit/c0702892a8d07219cd109fcb2085b577eec273d3))
* **debug:** the QA menu, and a session that never becomes data ([d47bb2e](https://github.com/Elijah-Dangerfield/Drop2048/commit/d47bb2e4f8af7a48b5f65259a61426889169ccf0))
* **game:** ▼ is a hard drop, soft drop is deleted ([6917a7f](https://github.com/Elijah-Dangerfield/Drop2048/commit/6917a7fa0db1b81145935a9201db68748174360a))
* **game:** build the audio path, pace the cascade by what it is worth ([1e1959a](https://github.com/Elijah-Dangerfield/Drop2048/commit/1e1959a6c75a3b12c661ac523f2ef64b8f30b7c3))
* **game:** playable board with transcript playback ([f34279c](https://github.com/Elijah-Dangerfield/Drop2048/commit/f34279cec10dadd2bfd50622fe29b64914ed3d08))
* **game:** rebuild the game screen on the design system ([fa1ca96](https://github.com/Elijah-Dangerfield/Drop2048/commit/fa1ca963864d74b6f84220d3da859db72eae5963))
* **ios:** add SKAdNetworkItems and update the privacy manifest for ads ([dc1791d](https://github.com/Elijah-Dangerfield/Drop2048/commit/dc1791dfbc6a8d8e0fa559217a8a54d5eed4de5f))
* **leaderboards:** sync achievements to the platform and cut the daily board ([afa4a00](https://github.com/Elijah-Dangerfield/Drop2048/commit/afa4a00e5d5560c173fcabf17f8d1a8079bb72b2))
* **legal:** publish privacy and terms on nightjarlabs.llc ([95ad60f](https://github.com/Elijah-Dangerfield/Drop2048/commit/95ad60fcd0a53138002a21ecdfc66975fdc9e47f))
* make the stats page and the Pro sheet look like the game ([acc948f](https://github.com/Elijah-Dangerfield/Drop2048/commit/acc948f54d583485fed64668eec7061c41802926))
* **progress:** persist runs, resume mid-cascade, and derive every stat ([3197d55](https://github.com/Elijah-Dangerfield/Drop2048/commit/3197d550b12a227909aca7f18f9e479e3f92b776))
* remove the Daily Challenge, make drag the default, and rework the ad policy ([14f0b41](https://github.com/Elijah-Dangerfield/Drop2048/commit/14f0b415e0d5a38c7ad6e2c9226a05d19c615b7f))
* rename the app to Doublestack and rebuild the site around the new icon ([af50438](https://github.com/Elijah-Dangerfield/Drop2048/commit/af50438c0447a3d905080d16cde184b52f8ee58a))
* **settings:** add settings, launch gates and the accessibility wiring ([655167b](https://github.com/Elijah-Dangerfield/Drop2048/commit/655167b481a86d23ad39ccbdba107d09893cbd35))
* **settings:** show the install ID, so a deletion request has a key ([fdb223c](https://github.com/Elijah-Dangerfield/Drop2048/commit/fdb223cf567af7c93a3df26b587afede1ffe792c))
* **telemetry:** commit the Sentry DSN and add the setup script ([5558f70](https://github.com/Elijah-Dangerfield/Drop2048/commit/5558f709f9d6d273e9c66e618465849b2152c707))
* the owner-directive channel, a draggable debug FAB and a QA menu ([8f340ce](https://github.com/Elijah-Dangerfield/Drop2048/commit/8f340ce2131af9ebd2a599d1197688258e6dc63e))
* **tools:** add the balance harness and measure the SPEC 5.3 spawn table ([bc0a4fe](https://github.com/Elijah-Dangerfield/Drop2048/commit/bc0a4fe99b1d2d3d8dfe5da2e8dee5e3b5b62572))
* **tutorial:** teach the nudge with a frozen clock ([ad992b6](https://github.com/Elijah-Dangerfield/Drop2048/commit/ad992b60145172e4352c9a47176fff51a0591744))
* **ui:** adopt the design handoff and add a screenshot harness ([0f843f2](https://github.com/Elijah-Dangerfield/Drop2048/commit/0f843f2f3cf8158620f27168b89f3e3989cb35ac))
* **ui:** one dark theme for every screen, and the defects a player hits first ([e049f95](https://github.com/Elijah-Dangerfield/Drop2048/commit/e049f95aafeedca8ee6fbccc0895e44e6ddd842c))
* **ui:** score counter, chain callout, level bar, danger border and specials ([546eb04](https://github.com/Elijah-Dangerfield/Drop2048/commit/546eb046b0fa8608a2c691295fb06fd01e363240))


### Bug Fixes

* **ads:** apply the serialization plugin in the compose convention, unbreaking iOS boot ([a4a8594](https://github.com/Elijah-Dangerfield/Drop2048/commit/a4a8594bddf3b85b3c46216559c8dfe9b02dfc29))
* **ads:** cap ad content at G on both platforms ([ccc652c](https://github.com/Elijah-Dangerfield/Drop2048/commit/ccc652cd8f08bb555cfe612bf24c08aa6521386e))
* **ads:** catch AdView construction, which composition put outside the guard ([5c3aed7](https://github.com/Elijah-Dangerfield/Drop2048/commit/5c3aed7eeb01241023351df120a552e54c2d1711))
* **ads:** load the banner on the main thread, which crashed 0.1.0+102 ([1f2922b](https://github.com/Elijah-Dangerfield/Drop2048/commit/1f2922bc04895991bb190ed11439d9448d1190a2))
* **cascade:** land merged blocks in the partner's cell in both orientations ([3b14046](https://github.com/Elijah-Dangerfield/Drop2048/commit/3b140463436da7eca54696905e6fcfae8e48def6))
* **game:** pause the run when anything covers the board ([58b0a64](https://github.com/Elijah-Dangerfield/Drop2048/commit/58b0a649e60526a980aadc856ff21e5254805042))
* **ios:** delete the dead camera bridge, which Apple rejected the build for ([bae112d](https://github.com/Elijah-Dangerfield/Drop2048/commit/bae112dca114a8fd608fe9fb90ab0d8c1eca7510))
* **ios:** flatten the icon's alpha and drop the iPad claim ([71faa91](https://github.com/Elijah-Dangerfield/Drop2048/commit/71faa91aba5e7d9945adc6de7b3dfa4a43d311f9))
* **ios:** name the shipped bundle Doublestack, not Drop 2048 ([1e9e78e](https://github.com/Elijah-Dangerfield/Drop2048/commit/1e9e78ea53baded810d6a38b355b8afe546a519e))
* **ios:** present ads from the top of the stack, not the window root ([be9dd79](https://github.com/Elijah-Dangerfield/Drop2048/commit/be9dd797dc6c5389aba485ace3f90f0e0599e160))
* **leaderboards:** stop Game Center drawing a second banner on iOS ([2dba743](https://github.com/Elijah-Dangerfield/Drop2048/commit/2dba7439bfa3e129d0e75fef86d644ae3e7a9585))
* make the app's claims about itself true, and validate R8 ([f893a04](https://github.com/Elijah-Dangerfield/Drop2048/commit/f893a04a29a3129bafc9e230df530a932530c308))
* **scripts:** make setup_legal_sync re-runnable, and check the token ([cac1997](https://github.com/Elijah-Dangerfield/Drop2048/commit/cac199732b990667a25b35d7c912ea4f57f4688e))

## 0.1.0

Initial version.
