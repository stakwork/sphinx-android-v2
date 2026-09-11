package chat.sphinx.concept_repository_dashboard.model

data class HivePoolStatus(
    val queuedCount: Int,
    val unusedVms: Int,
)

data class HivePod(
    val id: String,
    val subdomain: String,
    val state: String,
    val internalState: String?,
    val usageStatus: String?,
    val resourceUsage: HivePodResourceUsage?,
)

data class HivePodResourceUsage(
    val available: Boolean,
    val requestsCpu: String?,
    val requestsMemory: String?,
    val usageCpu: String?,
    val usageMemory: String?,
)

/**
 * Hive `mergeMetricsIntoVmData` semantics: identity/assignment from [basic],
 * live `state` / `internalState` / `resourceUsage` from the matching [full] row.
 * Basic-only ids are kept; full-only ids with required identity are appended.
 */
fun mergeHivePods(basic: List<HivePod>, full: List<HivePod>): List<HivePod> {
    val fullById = full.associateBy { it.id }
    val basicIds = basic.map { it.id }.toHashSet()
    val merged = basic.map { pod ->
        val overlay = fullById[pod.id] ?: return@map pod
        pod.copy(
            state = overlay.state,
            internalState = overlay.internalState,
            resourceUsage = overlay.resourceUsage,
        )
    }
    val extras = full.filter { it.id !in basicIds }
    return merged + extras
}

/**
 * Case-insensitive bucket sort. Unknown/other is last and is never folded into idle.
 */
fun sortHivePods(pods: List<HivePod>): List<HivePod> {
    return pods.sortedBy { pod ->
        val state = pod.state.lowercase()
        val usage = pod.usageStatus?.lowercase()
        when {
            state == "running" && usage == "used" -> 0
            state == "pending" -> 1
            state == "running" && usage != "used" -> 2
            state == "failed" -> 3
            else -> 4
        }
    }
}
