# Changelog

## [0.2.0](https://github.com/Elijah-Dangerfield/Drop2048/compare/v0.1.0...v0.2.0) (2026-09-16)


### ⚠ BREAKING CHANGES

* **game:** `Input.Nudge`, `EngineConfig.nudgeRows` and `SpeedCurve.softDropMsPerRow` are removed, along with the `speed.nudgeRows` and `speed.softDropMsPerRow` remote keys. Saved runs from earlier builds are refused.

### Features

* **achievements:** add badges, platform leaderboards and sharing, with their call sites ([5fee174](https://github.com/Elijah-Dangerfield/Drop2048/commit/5fee174fd2065359960926eb0433033dad0dff9a))
* add :libraries:cascade, the Drop 2048 game engine ([96e6b92](https://github.com/Elijah-Dangerfield/Drop2048/commit/96e6b9253af45f24768bda55b708e8d3d8a1ddbe))
* **ads:** a house ad network, QA config overrides, and ad tools in the debug menu ([81207c6](https://github.com/Elijah-Dangerfield/Drop2048/commit/81207c6d7202f36e646fa492ef1832a6a087e362))
* **ads:** SPEC 12 monetization — rewarded continue, Pro, and gated interstitials ([d8035c7](https://github.com/Elijah-Dangerfield/Drop2048/commit/d8035c7e16a3aac4b5e29d162e2cdde02484b8a0))
* **balance:** put the drop clock in the loop and re-cut the opening speed curve ([321070a](https://github.com/Elijah-Dangerfield/Drop2048/commit/321070ab65317fbac9854b845f1fbbcc231cc09c))
* block palettes, cue pairing and board geometry for the design system ([e6e95b3](https://github.com/Elijah-Dangerfield/Drop2048/commit/e6e95b3dd8b6196f0530717e0053055fdc702ffb))
* **config:** wire every SPEC 10 key through :libraries:config ([96f7c40](https://github.com/Elijah-Dangerfield/Drop2048/commit/96f7c40599c556f48243555adec86f24c27b60b9))
* **daily:** add the Daily Challenge ([c070289](https://github.com/Elijah-Dangerfield/Drop2048/commit/c0702892a8d07219cd109fcb2085b577eec273d3))
* **debug:** the QA menu, and a session that never becomes data ([d47bb2e](https://github.com/Elijah-Dangerfield/Drop2048/commit/d47bb2e4f8af7a48b5f65259a61426889169ccf0))
* **game:** ▼ is a hard drop, soft drop is deleted ([6917a7f](https://github.com/Elijah-Dangerfield/Drop2048/commit/6917a7fa0db1b81145935a9201db68748174360a))
* **game:** build the audio path, pace the cascade by what it is worth ([1e1959a](https://github.com/Elijah-Dangerfield/Drop2048/commit/1e1959a6c75a3b12c661ac523f2ef64b8f30b7c3))
* **game:** playable board with transcript playback ([f34279c](https://github.com/Elijah-Dangerfield/Drop2048/commit/f34279cec10dadd2bfd50622fe29b64914ed3d08))
* **game:** rebuild the game screen on the design system ([fa1ca96](https://github.com/Elijah-Dangerfield/Drop2048/commit/fa1ca963864d74b6f84220d3da859db72eae5963))
* **leaderboards:** sync achievements to the platform and cut the daily board ([afa4a00](https://github.com/Elijah-Dangerfield/Drop2048/commit/afa4a00e5d5560c173fcabf17f8d1a8079bb72b2))
* make the stats page and the Pro sheet look like the game ([acc948f](https://github.com/Elijah-Dangerfield/Drop2048/commit/acc948f54d583485fed64668eec7061c41802926))
* **progress:** persist runs, resume mid-cascade, and derive every stat ([3197d55](https://github.com/Elijah-Dangerfield/Drop2048/commit/3197d550b12a227909aca7f18f9e479e3f92b776))
* **settings:** add settings, launch gates and the accessibility wiring ([655167b](https://github.com/Elijah-Dangerfield/Drop2048/commit/655167b481a86d23ad39ccbdba107d09893cbd35))
* **telemetry:** commit the Sentry DSN and add the setup script ([5558f70](https://github.com/Elijah-Dangerfield/Drop2048/commit/5558f709f9d6d273e9c66e618465849b2152c707))
* the owner-directive channel, a draggable debug FAB and a QA menu ([8f340ce](https://github.com/Elijah-Dangerfield/Drop2048/commit/8f340ce2131af9ebd2a599d1197688258e6dc63e))
* **tools:** add the balance harness and measure the SPEC 5.3 spawn table ([bc0a4fe](https://github.com/Elijah-Dangerfield/Drop2048/commit/bc0a4fe99b1d2d3d8dfe5da2e8dee5e3b5b62572))
* **tutorial:** teach the nudge with a frozen clock ([ad992b6](https://github.com/Elijah-Dangerfield/Drop2048/commit/ad992b60145172e4352c9a47176fff51a0591744))
* **ui:** adopt the design handoff and add a screenshot harness ([0f843f2](https://github.com/Elijah-Dangerfield/Drop2048/commit/0f843f2f3cf8158620f27168b89f3e3989cb35ac))
* **ui:** one dark theme for every screen, and the defects a player hits first ([e049f95](https://github.com/Elijah-Dangerfield/Drop2048/commit/e049f95aafeedca8ee6fbccc0895e44e6ddd842c))
* **ui:** score counter, chain callout, level bar, danger border and specials ([546eb04](https://github.com/Elijah-Dangerfield/Drop2048/commit/546eb046b0fa8608a2c691295fb06fd01e363240))


### Bug Fixes

* **cascade:** land merged blocks in the partner's cell in both orientations ([3b14046](https://github.com/Elijah-Dangerfield/Drop2048/commit/3b140463436da7eca54696905e6fcfae8e48def6))
* make the app's claims about itself true, and validate R8 ([f893a04](https://github.com/Elijah-Dangerfield/Drop2048/commit/f893a04a29a3129bafc9e230df530a932530c308))

## 0.1.0

Initial version.
