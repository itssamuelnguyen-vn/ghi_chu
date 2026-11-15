package com.samuelcruz.ghichu.core

/**
 * Line-by-line comparison of two texts (like `git diff`), for "what changed" in a note's history. Plain Kotlin,
 * unit-tested.
 */
object Diff {
    enum class Op { SAME, ADDED, REMOVED }

    data class Line(val op: Op, val text: String)

    /** Above this many line pairs the middle part is shown as "all removed, all added" (keeps memory small). */
    private const val MAX_CELLS = 2_000_000

    fun lines(old: String, new: String): List<Line> {
        val a = old.split('\n')
        val b = new.split('\n')
        // Lines equal at the start and at the end need no work.
        var head = 0
        while (head < a.size && head < b.size && a[head] == b[head]) head++
        var tail = 0
        while (tail < a.size - head && tail < b.size - head && a[a.size - 1 - tail] == b[b.size - 1 - tail]) tail++
        val midA = a.subList(head, a.size - tail)
        val midB = b.subList(head, b.size - tail)

        val out = ArrayList<Line>(a.size + b.size)
        for (k in 0 until head) out += Line(Op.SAME, a[k])
        out += middle(midA, midB)
        for (k in a.size - tail until a.size) out += Line(Op.SAME, a[k])
        return out
    }

    /** Longest common subsequence of the lines; removed lines come before added ones at each change. */
    private fun middle(a: List<String>, b: List<String>): List<Line> {
        if (a.isEmpty()) return b.map { Line(Op.ADDED, it) }
        if (b.isEmpty()) return a.map { Line(Op.REMOVED, it) }
        if (a.size.toLong() * b.size > MAX_CELLS) return a.map { Line(Op.REMOVED, it) } + b.map { Line(Op.ADDED, it) }
        val n = a.size
        val m = b.size
        // lcs[i][j] = length of the common part of a[i..] and b[j..]
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = ArrayList<Line>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out += Line(Op.SAME, a[i]); i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { out += Line(Op.REMOVED, a[i]); i++ }
                else -> { out += Line(Op.ADDED, b[j]); j++ }
            }
        }
        while (i < n) out += Line(Op.REMOVED, a[i++])
        while (j < m) out += Line(Op.ADDED, b[j++])
        return out
    }

    /**
     * Only the changes with [context] unchanged lines around each; null marks lines left out ("…"). Empty when
     * nothing changed.
     */
    fun compact(lines: List<Line>, context: Int = 2): List<Line?> {
        if (lines.none { it.op != Op.SAME }) return emptyList()
        val keep = BooleanArray(lines.size)
        lines.forEachIndexed { k, l ->
            if (l.op != Op.SAME) for (x in (k - context).coerceAtLeast(0)..(k + context).coerceAtMost(lines.size - 1)) keep[x] = true
        }
        val out = ArrayList<Line?>()
        var skipped = false
        lines.forEachIndexed { k, l ->
            if (keep[k]) { out += l; skipped = false } else if (!skipped) { out += null; skipped = true }
        }
        return out
    }

    /** Number of added and removed lines. */
    fun counts(lines: List<Line>): Pair<Int, Int> = lines.count { it.op == Op.ADDED } to lines.count { it.op == Op.REMOVED }
}
