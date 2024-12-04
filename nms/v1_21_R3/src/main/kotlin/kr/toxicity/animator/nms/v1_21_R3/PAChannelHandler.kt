package kr.toxicity.animator.nms.v1_21_R3

import com.ticxo.playeranimator.api.PlayerAnimator
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.entity.LevelEntityGetter

class PAChannelHandler(
    private val player: ServerPlayer
) : ChannelDuplexHandler() {
    companion object {
        private val entityGetter = ServerLevel::class.java.declaredMethods.firstOrNull {
            LevelEntityGetter::class.java.isAssignableFrom(it.returnType) && it.returnType != LevelEntityGetter::class.java
        }?.apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        private fun getEntityGetter(level: ServerLevel): LevelEntityGetter<Entity> {
            return entityGetter?.let {
                it(level) as LevelEntityGetter<Entity>
            } ?: level.`moonrise$getEntityLookup`()
        }
    }

    private fun getEntityAsync(world: ServerLevel, id: Int): org.bukkit.entity.Entity? {
        return getEntityGetter(world).get(id)?.bukkitEntity
    }

    override fun write(ctx: ChannelHandlerContext?, msg: Any, promise: ChannelPromise?) {
        if (msg is ClientboundAddEntityPacket) {
            handleEntitySpawn(msg.id)
        } else if (msg is ClientboundRemoveEntitiesPacket) {
            msg.entityIds.forEach {
                handleEntityDespawn(it)
            }
        }
        super.write(ctx, msg, promise)
    }

    private fun handleEntitySpawn(id: Int) {
        val entity = getEntityAsync(player.serverLevel(), id)
        val model = PlayerAnimator.api.modelManager.getPlayerModel(entity)
        model?.spawn(player.bukkitEntity)
    }

    private fun handleEntityDespawn(id: Int) {
        val entity = getEntityAsync(player.serverLevel(), id)
        val model = PlayerAnimator.api.modelManager.getPlayerModel(entity)
        model?.despawn(player.bukkitEntity)
    }
}