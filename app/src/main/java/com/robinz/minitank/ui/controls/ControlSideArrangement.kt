package com.robinz.minitank.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Moves control groups without mirroring their labels, arrows, or input axes. */
fun controlSideArrangement(
    swapped: Boolean,
    base: Arrangement.Horizontal = Arrangement.Start
): Arrangement.Horizontal = object : Arrangement.Horizontal {
    override val spacing = base.spacing

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray
    ) {
        with(base) { arrange(totalSize, sizes, LayoutDirection.Ltr, outPositions) }
        if (swapped) {
            sizes.indices.forEach { i ->
                outPositions[i] = totalSize - outPositions[i] - sizes[i]
            }
        }
    }
}
