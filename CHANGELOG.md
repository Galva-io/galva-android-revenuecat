# Changelog

## 1.0.2

- Builds on Galva Android SDK `galva-sdk` **1.5.2** (was 1.5.1).
- **Fixes in-app messages not shown with `autoDisplayInAppMessages = true`** when `Galva.configure` runs after an activity is already resumed. The SDK only learned of an activity from `onActivityResumed` after `configure`, so it logged `In-app message <id> not shown — no visible activity` until the user left the app and came back. galva-sdk 1.5.2 uses the activity passed to `configure` when that activity is resumed. Apps that configure in `Application.onCreate`, as the README shows, weren't affected.
- No API changes.

## 1.0.1

- Builds on Galva Android SDK `galva-sdk` **1.5.1** (was 1.3.0).
- **Fixes a crash: `IllegalStateException: Already resumed`** on the main thread, from Galva's Play Billing code.
  - Cause: `PlayBillingLauncher.queryStorefrontCountryCode()` wraps Play Billing's `getBillingConfigAsync` in `suspendCancellableCoroutine` and resumed it on every callback. Play Billing 9.1.0 sometimes calls that listener twice: the first call resumes the coroutine, then a second, error response tried to `resume(null)` again. The exception is thrown on Play Billing's handler on the main looper, so the app crashed.
  - Apps can't catch it: the class is internal to the SDK and the callback runs on Play Billing's own handler.
  - galva-sdk 1.5.1 ignores any callback after the first, so updating this package is the fix.
- Also picked up from galva-sdk 1.4.0–1.5.0: `Galva.purchase(...)`, and the `obfuscatedAccountId` Galva attaches to its own purchases is no longer hashed (`GalvaPurchaseRequest.obfuscatedAccountId` may now be empty). Purchases made through RevenueCat are unaffected.

## 1.0.0

- First release.
