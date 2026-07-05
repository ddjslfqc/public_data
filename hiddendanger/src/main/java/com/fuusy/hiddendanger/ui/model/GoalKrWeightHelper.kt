package com.fuusy.hiddendanger.ui.model

/** KR 权重分配：默认均分，联动滑块调整后总和恒为 100 */
object GoalKrWeightHelper {

    const val TOTAL = 100
    const val MIN_WEIGHT = 1

    fun distributeEqually(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(TOTAL)
        val base = TOTAL / count
        val remainder = TOTAL - base * count
        return List(count) { index -> base + if (index == 0) remainder else 0 }
    }

    /** 拖动某条 KR 权重时，其余 KR 按比例分摊剩余权重 */
    fun adjustLinked(weights: IntArray, changedIndex: Int, newWeight: Int): IntArray {
        val n = weights.size
        if (n <= 0) return intArrayOf()
        if (n == 1) return intArrayOf(TOTAL)

        val maxForChanged = TOTAL - (n - 1) * MIN_WEIGHT
        val clamped = newWeight.coerceIn(MIN_WEIGHT, maxForChanged)

        val result = IntArray(n)
        result[changedIndex] = clamped
        val remaining = TOTAL - clamped

        val others = (0 until n).filter { it != changedIndex }
        val otherWeightSum = others.sumOf { weights[it].coerceAtLeast(MIN_WEIGHT) }

        var assigned = 0
        others.forEachIndexed { idx, i ->
            if (idx == others.lastIndex) {
                result[i] = (remaining - assigned).coerceAtLeast(MIN_WEIGHT)
            } else {
                val share = if (otherWeightSum > 0) {
                    (remaining * weights[i] / otherWeightSum).coerceAtLeast(MIN_WEIGHT)
                } else {
                    remaining / others.size
                }
                result[i] = share
                assigned += share
            }
        }

        val drift = TOTAL - result.sum()
        if (drift != 0) {
            val fixIndex = others.last()
            result[fixIndex] = (result[fixIndex] + drift).coerceAtLeast(MIN_WEIGHT)
        }
        return result
    }

    fun total(weights: List<Int>): Int = weights.sum()
}
