package kr.toxicity.animator.nms.v1_21_R3

import com.ticxo.playeranimator.api.nms.IRangeManager
import net.minecraft.server.level.ChunkMap
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.lang.reflect.Field

class RangeManager(private val tracker: ChunkMap.TrackedEntity) : IRangeManager {
    companion object {
        val range: Field = ChunkMap.TrackedEntity::class.java.declaredFields.first {
            it.type == Integer.TYPE
        }.apply {
            isAccessible = true
        }
    }

    override fun addPlayer(p0: Player) {
        tracker.seenBy.add((p0 as CraftPlayer).handle.connection)
    }

    override fun removePlayer(p0: Player) {
        tracker.seenBy.remove((p0 as CraftPlayer).handle.connection)
    }

    override fun setRenderDistance(p0: Int) {
        range.set(tracker, p0)
    }

    override fun getPlayerInRange(): Set<Player> = tracker.seenBy.map {
        it.player.bukkitEntity
    }.toSet()
}