package cc.dlabs.pesamind.core.ui

import java.text.NumberFormat
import java.util.Locale

// ─── Currency formatting ──────────────────────────────────────────────────────
//
// The canonical "UGX 1,234,567" formatter. Lifted here from
// features/settings/channels/ChannelScreen.kt, which was already exporting it as
// `internal` so the onboarding flow could reuse it — a feature package is the
// wrong home for something three other features import.
//
// docs/vault/02-reuse-clusters.md records eight independent copies of this
// NumberFormat block across the app. This file is where they should converge;
// consolidating the rest is out of scope for the subscription work and is
// tracked there, not silently half-done here.

private val ugxFormat = NumberFormat.getNumberInstance(Locale.US)

/** Formats an amount as `UGX 1,234,567`. */
fun Double.asUgx(): String = "UGX ${ugxFormat.format(this)}"

/**
 * Formats an amount without the currency prefix, for places that render "UGX" as
 * a separate element (a label above the figure, a leading `Text`, ...).
 */
fun Double.asUgxAmount(): String = ugxFormat.format(this)
