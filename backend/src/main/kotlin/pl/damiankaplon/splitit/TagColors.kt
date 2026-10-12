package pl.damiankaplon.splitit

/**
 * Palette new tags draw their color from; colors are persisted as
 * `#rrggbb`.
 */
object TagColors {
    private val palette = listOf(
        "#ef4444", "#f97316", "#f59e0b", "#84cc16", "#22c55e", "#14b8a6",
        "#06b6d4", "#3b82f6", "#6366f1", "#8b5cf6", "#d946ef", "#ec4899",
    )

    fun random(): String = palette.random()
}
