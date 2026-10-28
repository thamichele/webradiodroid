# Work Log

## Current state

No active task. **Gradle standardisation is complete for this repo** (plan: `workbench/PLAN_GRADLE_STANDARDISATION.md`): P4c done 2026-09-11, P5 `non-free` merge done 2026-09-13 (`a2cc70c0`), P6 fdroiddata recipe MR !49334 merged 2026-09-18 — **the release freeze from `097b40ba` is over.** Released since: v4.3.9 (code 116, 2026-09-17) and v4.3.10 (code 117, 2026-10-13), both on F-Droid. The `non-free` branch (Google Cast, hence Play Services) is kept merged with master, last on 2026-10-13. P4c reference numbers (2026-09-11, pre-release): `app-release-unsigned.apk` **3,333,742 B**, `mapping.txt` **37,182,512 B / 336,805 lines**, `app-debug.apk` **12,450,633 B**; **44 actionable release tasks incremental / 45 clean, 36 debug / 37 clean**; `releaseRuntimeClasspath` **706 lines, sha256 `00ee2a8cc5ab727235510ecf639cd70eee998d8b918d78b9c4b4512a0c4e626a`**. Re-derive rather than trust.

**Transistor keeps the standalone Kotlin plugin** — a documented exception to the shared target spec, not an oversight. `@Parcelize` is inert under AGP's built-in Kotlin; settled on Trackbook and inherited here, see Decisions below.

This is the first work log in this repo. There is no `.ai/WORK_PLAN.md` and there should not be one: the plan for this task is the cross-repo file named above.

## Open issues

- **The `builtInKotlin=false` / `newDsl=false` exception expires at AGP 10.** The deprecation the Kotlin DSL surfaces states that `BaseAppModuleExtension` "will be removed in AGP 10.0", so the flag selects a class AGP has scheduled for deletion. At AGP 10 the choice returns: built-in Kotlin, which `@Parcelize` blocks, or hand-written `Parcelable`. Found on Trackbook 2026-09-07; it applies identically here, and the cross-repo plan's phrase "a stable end state" holds under AGP 9.4 only.
- **No security audit yet.** Audits now start from a threat model: workbench `JOB_THREAT_MODEL` → `docs/THREAT_MODEL.md` → `JOB_RUN_AUDIT` (the seeded `SECURITY_AUDIT_STARTPROMPT.md` was removed 2026-10-18). Escapepod's audit findings on XML and download vectors are the ones meant to transfer.

## Decisions

- 2026-09-11 — **Qualify `colorPrimary` through appcompat, not Material.** Why: it does not exist in `com.google.android.material.R.attr` — Material consumes the appcompat attribute rather than redeclaring it, so the Material spelling fails to compile. `colorErrorContainer` is Material's own and takes the Material spelling. The two `MaterialColors.getColor` call sites in `UiHelper.kt` are therefore qualified through different libraries, which looks inconsistent and is correct; **both lines now carry a comment saying so**, so this entry records the finding, not the instruction.
- 2026-09-09 — **P4c inherits P4b's settled decisions rather than re-deriving them.** Why: Trackbook is the app that paid for them, and both apps sit on the same two-app exception, so re-litigating costs builds and risks diverging. Inherited verbatim: (a) **keep `kotlin-android`**, `kotlinOptions { jvmTarget }` and the explicit `kotlin-stdlib-jdk8`, because `kotlin-parcelize` is inert under AGP's built-in Kotlin — the plugin applies without error but generates nothing, and `kotlin-parcelize-runtime` fixes only the import; (b) **keep `android.builtInKotlin=false` and `android.newDsl=false`** in `gradle.properties`, **with the comments already on them** — Transistor is where those comments were written, so unlike Trackbook nothing has to be composed; (c) take the module straight from bare `id '…'` to `alias(libs.plugins…)`, skipping Escapepod's intermediate step; (d) keep `-keepattributes SourceFile,LineNumberTable` in `rules.keep`. Alternative considered and rejected: hand-write `Parcelable` in the model classes to reach the plain spec — app-code work outside this task, with regression risk in code nothing tests automatically.
- 2026-09-09 — **P4c.5 is one measured step, not P4b.6's two.** Why: `-keepattributes SourceFile,LineNumberTable` is **already active** in `app/proguard-rules.pro` here, so moving the file to `src/main/keepRules/rules.keep` introduces no behaviour change and needs no second baseline. P4b.6 had to split because it was activating the directive at the same time. Consequence: P4c.6 takes its size and `mapping.txt` numbers straight from the post-P4c.5 state.
- 2026-09-09 — **Number P4c's iterations 1–7 with no gap.** Why: the extra iteration that made P4b eight was the Kotlin-plugin cluster, which "Resolved by probe" settled before this phase was planned, so there is nothing to cancel in place. P4b.3's cancelled ID stays cancelled *in P4b* — that convention exists to keep cross-references resolving in a file that already published them, and it does not apply to a phase being planned fresh.

## History

### 2026-10-18 — State synced after P5/P6
- Outcome: done
- Summary: "Current state" still named P5 as next and the release freeze as active. Both were stale: P5 closed 2026-09-13, recipe MR !49334 merged 2026-09-18, v4.3.9 and v4.3.10 shipped. Removed the two resolved open issues (P6 `prebuild` rewrite, P5 merge). Also removed the obsolete `.ai/SECURITY_AUDIT_STARTPROMPT.md` (`4fb71cfd`); audits now start from `docs/THREAT_MODEL.md`.

### 2026-09-11 — Phase P4c complete
- Outcome: done
- Summary: Transistor converted to the shared Gradle setup in seven iterations — Gradle 9.6.1 / AGP 9.4.0, version catalog, repositories in settings, Kotlin DSL, R8 via `optimization` + `keepRules`, `gradle.properties` to spec, `localeFilters`. Release APK 3,333,742 B; `releaseRuntimeClasspath` byte-identical to the pre-phase baseline all seven times.
- Open issues carried forward: Transistor release-frozen from `097b40ba` until the P6 recipe MR; the `builtInKotlin=false` / `newDsl=false` exception expires at AGP 10; `.ai/SECURITY_AUDIT_STARTPROMPT.md` seeded and not started.
- Note: **three of the seven iterations found the plan's stated measurement method wrong** — P4c.5 (a null `mapping.txt` diff proves nothing alone), P4c.6 (APK file size cannot measure the `nonTransitiveRClass` sweep; 16 KB page alignment absorbs it), P4c.7 (the `resourceConfigurations` deprecation it claimed to retire was never emitted). None changed what was built; all three would have misled the next reader. The plan's predictions about *code* held; its predictions about *measurement* did not.

### 2026-09-11 — P4c.7 — `resourceConfigurations` → `localeFilters`
- Outcome: done
- Files changed:
    - `app/build.gradle.kts`
- Notes: One attempt, no retry, and the strongest acceptance in the phase: `aapt2 dump badging`, `aapt2 dump configurations` **and the release APK itself are all byte-identical**. No device test — the artifact is the same bytes, so there is nothing to exercise. 42 locales carried verbatim including `nb-rNO`, `pt-rBR` and `zh-rCN`; the comment moved above the list in Trackbook's and Escapepod's two-line wording (y20k, 2026-09-11), replacing Transistor's own one-liner that sat below it.
    - **The plan's deprecation claim was false and is now measured.** It expected this iteration to retire a `resourceConfigurations` deprecation warning, leaving `BaseAppModuleExtension` and `packagingOptions`. Stashing the change and running the configuration phase both ways gives **identical warning sets**: there was no `resourceConfigurations` deprecation to retire, and `packagingOptions` does not appear at all because Transistor never used it. The list both ways is the two-app exception plus KGP's deprecated-plugin notice.
    - **The two lists really are in sync**: 42 entries in `app/build.gradle.kts` and 42 in `res/xml/locales_config.xml`, identical, `-r` form on both sides, with `android:localeConfig` declared at `AndroidManifest.xml:23`. Unlike Trackbook, where the same file is orphaned, the hand-sync this iteration preserves is load-bearing.
    - **Baseline correction carried from P4c.6**: the release APK on the committed tree is **3,333,742 B**, not the 3,333,746 B recorded in the P4c.6 entry. The four bytes are the two source comments added to `UiHelper.kt` after that measurement was taken — `-keepattributes SourceFile,LineNumberTable` bakes source line numbers into the dex, so **a comment-only edit is not a no-op in a release build here**. It rewrote 122 lines of `mapping.txt`, all inside `UiHelper` and its two inner classes. P4c.6's conclusion is unaffected (comments were absent on both sides of that comparison).

### 2026-09-11 — P4c.6 — `gradle.properties` to spec + `nonTransitiveRClass` sweep
- Outcome: done
- Files changed:
    - `gradle.properties`
    - `app/src/main/java/org/y20k/transistor/helpers/AppThemeHelper.kt` (line 100)
    - `app/src/main/java/org/y20k/transistor/helpers/UiHelper.kt` (lines 238, 308)
- Notes: One retry, and the plan's own caveat is what it bought. `gradle.properties` is now **Voxscribe's file byte-identical through line 19** plus the four exception lines — the two comments Transistor already carried, kept verbatim as planned. `releaseRuntimeClasspath` byte-identical for the **sixth** time (706 lines, `00ee2a8c…`). Device test passed (y20k): dynamic colours, and both swipe backgrounds.
    - **The compiler found exactly the three predicted sites, and corrected the attribution on one.** The plan's table called all three Material. `colorErrorContainer` is Material; **`colorPrimary` is appcompat** (`androidx.appcompat.R.attr.colorPrimary`) and does not resolve through `com.google.android.material.R` at all — it is an appcompat attribute that Material consumes. That was the retry. The plan's warning that "`R.attr` lookups can resolve through appcompat as well" was correct and is the reason this cost one build instead of a debug session. Both call sites now carry a comment; see Decisions.
    - **Null delta on the binaries, for two different reasons — do not merge them.** Release: `app-release-unsigned.apk` and `mapping.txt` both **byte-identical**, because R8 was already stripping the transitive R classes. The evidence is `usage.txt`, R8's list of what it *deleted*, which lost **227,691 B / 4,371 lines**: 9 generated R subclasses (`R$attr`, `R$styleable`, `R$dimen`, `R$bool`, `R$plurals`, …) and 4,362 of their fields. Less garbage in, identical binary out.
    - **Debug: the dex genuinely shrank and the APK size still did not move.** `classes2.dex` went 732,228 → 587,400 B and the archive's total compressed content dropped **30,581 B** (12,233,831 → 12,203,250), but `app-debug.apk` stayed at exactly **12,450,633 B** — dex entries are 16 KB page-aligned and the slack grew to absorb the saving. **Trackbook's 32,768 B drop at P4b.7 is exactly 2 × 16,384**: same change, Trackbook crossed two page boundaries and Transistor crossed none. **APK file size is the wrong metric for this iteration; compressed content size is the right one.**
    - **`seeds.txt` reordered again, sorted diff zero** — the P4c.5 gotcha confirmed on a second, unrelated change. It is a property of R8, not of that one iteration.
    - Deprecation inventory under `--warning-mode all --no-configuration-cache` is **unchanged from P4c.4**: the two exception flags, KGP's deprecated-plugin notice, and AGP's own `applicationVariants` / `testVariants` / `unitTestVariants` warnings, which are not ours. Nothing new appeared.

### 2026-09-11 — P4c.5 — R8 to the `optimization` block
- Outcome: done
- Files changed:
    - `app/build.gradle.kts`
    - `app/proguard-rules.pro` (deleted)
    - `app/src/main/keepRules/rules.keep` (new)
- Notes: One step as planned, one attempt, no retry. **Null delta on every axis** — `app-release-unsigned.apk` byte-identical at **3,333,746 B**, `mapping.txt` byte-identical at **37,182,512 B / 336,805 lines**, `usage.txt` byte-identical, `releaseRuntimeClasspath` byte-identical for the **fifth** time (706 lines, `00ee2a8c…`). Line-number entries 292,554 and `sourceFile` records 3,911, both unchanged — the acceptance criterion "`mapping.txt` still carries line numbers" is met by the mapping not moving at all. **Fifth app with a null delta on this iteration.** Device test passed (y20k): install, playback, kill-and-resume.
    - **`seeds.txt` is the only output that differs, and only in ordering** — 2,141 lines both times, and `diff` on the sorted files returns nothing. R8's seed enumeration order is not stable across a change of rule source. The 168 seeded members across `core.**`, `playback.PlayerService` and `search.RadioBrowserResult` all survive. **Do not read a raw `seeds.txt` diff as a regression** in P4c.6 or P4c.7.
    - **`configuration.txt` is where the proof actually landed.** The five directives moved out of the `proguard-rules.pro` section into a `keepRules` section (`extracted file: …/keepRules/rules.keep`), while the mapping stayed byte-identical — so `keepRules/*.keep` honours the two non-`-keep` attribute directives. The plan expected a `mapping.txt` diff to settle this; it settled by not moving. Note this is the same file P4b.6 warned records what R8 was *handed*, not R8's own defaults — it is being read here for rule provenance, not behaviour.
    - **`rules.keep` is identical to Escapepod's through line 20** and diverges only in the keeps: three rather than two, and Transistor's own comment wording ("the core classes") kept, since its `core.**` keep is narrower than Escapepod's `search.results.*`. The `buildTypes` block came out **byte-identical to Trackbook's**, empty `debug { }` included (y20k, 2026-09-11 — Trackbook's precedent over Voxscribe's no-debug-block form, to leave an obvious place to re-enable shrink testing).
    - **Task counts: 44 actionable incremental, 45 on a `clean` run** — the extra one is `clean` itself. P4c.4's 44 is unchanged.
    - **The CLI and Android Studio produce differently named release artifacts** — `./gradlew assembleRelease` gives `app-release-unsigned.apk` (3,333,746 B), Studio gives a signed `app-release.apk` (3,337,842 B). The work log's numbers are CLI numbers. Both sides of a size comparison must come from the same path.

### 2026-09-11 — P4c.4 — Convert to Kotlin DSL
- Outcome: done
- Files changed:
    - `settings.gradle` → `settings.gradle.kts`
    - `build.gradle` → `build.gradle.kts`
    - `app/build.gradle` → `app/build.gradle.kts`
- Notes: **One attempt, no retry** — the retry P4b.5 spent on Trackbook was pre-paid by the plan predicting the `kotlinOptions` move. Debug APK **12,450,633 B**, unchanged from P4c.1; `releaseRuntimeClasspath` byte-identical for the **fourth** time (706 lines, `00ee2a8c…`). **First release build of the phase: `app-release-unsigned.apk` at 3,333,746 B** — the number P4c.5 baselines against. `assembleRelease` runs 44 actionable tasks to debug's 37. **Device test passed (y20k): install, adding stations, playback.**
    - **`settings.gradle.kts` and `build.gradle.kts` came out byte-identical to Trackbook's** — `diff` returns nothing for either. Voxscribe differs only by `rootProject.name`, which Transistor has never carried and none was added.
    - `kotlinOptions { jvmTarget = '17' }` left `android` for a top-level `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }` with `import org.jetbrains.kotlin.gradle.dsl.JvmTarget` at the head — Trackbook's placement and spelling. **The two Groovy space-assignment deprecations are retired** with the file format, as predicted.
    - Remaining warnings are the two-app exception only — `builtInKotlin=false`, `newDsl=false`, KGP's deprecated-plugin notice. No `e:` errors, so the plan's "grep `> Configure project` for `e:`" precaution never got used.
    - **`--warning-mode all` reports nothing when the configuration cache hits** — the configuration phase never runs, so there is nothing to warn about. It needs `--no-configuration-cache` to be meaningful. Cost one confused re-run here; worth knowing for P4c.6.
    - **The module file reads as delete+add at git's default 50% similarity threshold**, and as a rename from 30% down; the other two are clean renames. `git log --follow` still traces it.
    - The commented-out debug shrinking block was converted along with the live code (`//            isMinifyEnabled = true`), so uncommenting it still works. Trackbook dropped those lines instead; Transistor keeps them.

### 2026-09-11 — Quick fix — `gradlew` executable
- Outcome: done
- Files changed:
    - `gradlew` (mode only, `100644 → 100755`)
- Notes: `c68b876a`. **One move, not the two predicted**: `core.filemode` is `true` in this clone, so a plain `chmod +x` fixed the working file and gave git the mode change to record — `git update-index --chmod=+x` was never needed, and the staged version of it found on 2026-09-11 was the harder path to the same result. `./gradlew -v` runs without the `sh` prefix. **Builds from P4c.4 on drop the workaround.** Transistor now matches Trackbook, Escapepod, Stay Put and Voxscribe at `100755`. The `non-free` branch still carries `100644` and picks this up at the P5 merge.

### 2026-09-11 — P4c.3 — Repositories into settings, root reduced, module aliases
- Outcome: done
- Files changed:
    - `settings.gradle`
    - `build.gradle`
    - `app/build.gradle`
- Notes: One attempt, no retry. **38 → 37 actionable tasks**, the drop the plan predicted, and `clean` survives its own deletion — the base plugin supplies it, so removing the legacy `task clean(type: Delete)` costs nothing. `app-debug.apk` **12,450,633 B**, unchanged from P4c.1/P4c.2; `releaseRuntimeClasspath` byte-identical to the baseline for the **third** time (706 lines, sha256 `00ee2a8c…`), `com.android.support` still zero.
    - **`settings.gradle` was already edited and uncommitted in the working tree when this session started** — not committed, not logged, not mentioned in the plan. Verified against the three sibling apps before adopting it: identical byte-for-byte apart from Groovy's `include ':app'`. Adopted unchanged rather than reverted and redone. **The lesson is the state, not the edit: a correct change nobody recorded is indistinguishable from an abandoned experiment until it is diffed against a sibling.**
    - Root `build.gradle` is now Trackbook's file with `kotlin.android` where Escapepod has `ksp`. It **gained a trailing newline** Transistor never had, so the diff shows `\ No newline at end of file` on the removed side.
    - The module's `kotlin-parcelize` **stays a bare `id`** and needs no catalog alias — the root `plugins` block puts KGP on the shared classpath, settled in P4b.4. The two plugin aliases P4c.2 left declared and unreferenced are now consumed, closing that loop.
    - `grep -r foojay` matches only `gradle/gradle-daemon-jvm.properties`, which stays, and this log's own prose about it.
    - **A staged `git update-index --chmod=+x gradlew` was also found at session start** and was unstaged to keep an out-of-scope mode change out of this diff. `gradlew` is still `100644`; the phase continues under `sh gradlew`.

### 2026-09-09 — P4c.2 — Version catalog
- Outcome: done
- Files changed:
    - `gradle/libs.versions.toml` (new)
    - `app/build.gradle`
- Notes: One attempt, no retry. **18 versions, 21 libraries, 2 plugins** — the counts the plan predicted, verified against the module first. `clean assembleDebug` succeeded, still **38 actionable tasks**, and `app-debug.apk` came out at **12,450,633 B, byte-identical to P4c.1's**. `releaseRuntimeClasspath` byte-identical to the P4c.1 baseline for the second time (706 lines, sha256 `00ee2a8c…`, empty diff). Only `versionName "4.3.8"` remains as a literal in `app/build.gradle`, as specified; AGP's and Kotlin's literals stay in the root `buildscript`, which sees no catalog accessors.
    - **Escapepod's catalog covered 17 of the 21 aliases name-for-name at identical versions**, so almost nothing was invented. Four exceptions: `constraintlayout` stays on Transistor's **2.2.2** (Escapepod is on 2.2.1 — this iteration changes no versions); `androidx-media3-exoplayer-hls` is new, since Escapepod has no HLS; `kotlin-stdlib-jdk8` takes **Trackbook's** naming and `kotlin` version ref; and `kotlin = "2.3.21"`, not Trackbook's 2.3.10. `[versions]` follows Escapepod's ordering, `[libraries]` too with the stdlib first as in Trackbook, so all five catalogs stay comparable.
    - **`libs.androidx.media3.exoplayer` and `libs.androidx.media3.exoplayer.hls` coexist** — an alias that is a strict prefix of another. Gradle generates the nested accessor and the leaf value together; no precedent for this in the sibling catalogs, and it needed no special handling.
    - **Byte-identical resolution is what verifies the alias set**, not reading the file: a misnamed accessor either fails configuration or silently resolves elsewhere, and 706 unchanged lines rule both out for all 21.
    - **The Gradle 10 hard error found in P4c.1 is retired**, as predicted — zero `implicit lookup` hits under `--warning-mode all`. `$kotlinVersion` was the module's only read of a root `ext` property, so the catalog closed it as a side effect and it needed no iteration of its own.
    - `agp = "9.4.0"`, `kotlin`, and the `android-application` / `kotlin-android` plugin aliases are **declared and unreferenced**, waiting for P4c.3. The commented-out `fileTree` line went with the three `def` locals; dependency order and blank-line grouping are unchanged, so the diff is coordinate-for-coordinate.

### 2026-09-09 — P4c.1 — Gradle 9.6.1 + AGP 9.4.0
- Outcome: done
- Files changed:
    - `gradle/wrapper/gradle-wrapper.properties`
    - `build.gradle`
    - `.gitignore`
- Notes: Three lines, one attempt, no retry. Wrapper `9.6.0` → `9.6.1`, root `ext.gradlePluginVersion` `9.2.1` → `9.4.0`, `kotlinVersion` untouched at 2.3.21, `/app/release` appended to `.gitignore`. Baseline captured **before the first edit**, as the plan required: **706 lines, sha256 `00ee2a8c…`**. `clean assembleDebug` succeeded in 58s, **38 actionable tasks**, one `app-debug.apk` at **12,450,633 B** — no splits, so the single-file shape the plan predicted. `gradlew -v` confirms Gradle 9.6.1; `buildEnvironment` confirms **AGP 9.4.0**, which pulls KGP 2.2.10 and is forced up to **2.3.21** by the root `ext`.
    - **The AGP bump moved no dependency.** `releaseRuntimeClasspath` is byte-identical before and after — same 706 lines, same sha256, empty diff. Second app where the toolchain step is *itself* proven pure (P4b.1 was the first), so any resolution drift in P4c.2–.7 is attributable to that iteration rather than to AGP.
    - **`com.android.support` count on `releaseRuntimeClasspath`: zero.** That is P4c.6's `enableJetifier` check, answered here on the real tree rather than inherited. The plan called Transistor's pre-existing `enableJetifier=false` a provable no-op; this is the proof, and it matches P4b.1's result on Trackbook.
    - Editing `distributionUrl` by hand left `gradle/gradle-daemon-jvm.properties` untouched — still JetBrains vendor, `toolchainVersion=21`, ten foojay `toolchainUrl` lines. **P4c.3's `grep -r foojay` assumes exactly that**, so it is safe.
    - `.gitignore` **has no trailing newline** and was kept that way, so the diff shows `/captures` as touched while its content is unchanged — a `\ No newline at end of file` marker moving down one line. `app/release/` (`baselineProfiles/`, `output-metadata.json`) has dropped out of `git status`.
    - **Deprecation inventory under `--warning-mode all`, since it maps onto later iterations.** Expected and permanent: `builtInKotlin=false`, `newDsl=false`, and KGP's own "no longer required since AGP 9.0" notice — the two-app exception announcing itself, and the running signal that it expires at AGP 10. Retired by P4c.4: Groovy space-assignment at `app/build.gradle:8` (`namespace`) and `:42` (`shrinkResources`). Not ours: `testVariants` / `unitTestVariants` obsolete, from AGP's own legacy variant API.
    - **One deprecation is new and is not in P4b.1's notes: implicit property lookup in a parent project**, at `app/build.gradle:79` — `implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion"` resolves `kotlinVersion` from the root project, which Gradle says "will fail with an error in Gradle 10". **P4c.2 retires it for free**, because the catalog alias replaces that `ext` reference. Worth recording as a second reason for the version-catalog move, not just tidiness.
    - **`gradlew` is not executable in this repo** — see Open issues. Every build in this phase runs as `sh gradlew`.
