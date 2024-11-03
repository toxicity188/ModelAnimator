package kr.toxicity.animator.nms.v1_21_R1

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.datafixers.util.Pair
import com.ticxo.playeranimator.api.nms.INMSHandler
import com.ticxo.playeranimator.api.nms.IRangeManager
import com.ticxo.playeranimator.api.nms.IRenderer
import com.ticxo.playeranimator.api.texture.TextureWrapper
import io.netty.channel.Channel
import io.netty.channel.ChannelPipeline
import kr.toxicity.animator.api.nms.NMS
import net.minecraft.core.component.DataComponents
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerCommonPacketListenerImpl
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.component.ResolvableProfile
import org.bukkit.Material
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot.*
import org.bukkit.inventory.ItemStack
import java.util.*

class NMSImpl: NMS, INMSHandler {
    companion object {
        private val connectionGetter = ServerCommonPacketListenerImpl::class.java.declaredFields.first {
            it.type == Connection::class.java
        }.apply {
            isAccessible = true
        }
        fun getConnection(serverPlayer: ServerPlayer) = connectionGetter[serverPlayer.connection] as Connection
    }

    override fun createRenderer(): IRenderer = DisplayRendererImpl(this)
    override fun getTexture(player: Player): String {
        return (player as CraftPlayer).handle.gameProfile.properties.get("textures").first().value
    }

    override fun setSkullTexture(skull: ItemStack?, texture: TextureWrapper): ItemStack {
        return CraftItemStack.asBukkitCopy(CraftItemStack.asNMSCopy(skull ?: ItemStack(Material.PLAYER_HEAD)).apply {
            set(DataComponents.PROFILE, ResolvableProfile(GameProfile(UUID.randomUUID(), "").apply {
                properties.put("textures", Property("textures", texture.toBase64()))
            }))
        })
    }
    override fun sendItemChange(player: Player, itemStackMap: Map<org.bukkit.inventory.EquipmentSlot, ItemStack>, targetPlayers: Collection<Player>) {
        val packet = ClientboundSetEquipmentPacket(
            (player as CraftPlayer).handle.id,
            itemStackMap.map {
                Pair.of(
                    when (it.key) {
                        HEAD -> EquipmentSlot.HEAD
                        CHEST -> EquipmentSlot.CHEST
                        LEGS -> EquipmentSlot.LEGS
                        FEET -> EquipmentSlot.FEET
                        HAND -> EquipmentSlot.MAINHAND
                        OFF_HAND -> EquipmentSlot.OFFHAND
                        BODY -> EquipmentSlot.BODY
                    },
                    CraftItemStack.asNMSCopy(it.value)
                )
            }
        )
        targetPlayers.forEach {
            (it as CraftPlayer).handle.connection.send(packet)
        }
    }

    override fun injectPlayer(player: Player) {
        val ply: ServerPlayer = (player as CraftPlayer).handle
        val cdh = PAChannelHandler(ply)
        val connection: Connection = getConnection(ply)
        val pipeline: ChannelPipeline = connection.channel.pipeline()
        for (name in pipeline.toMap().keys) {
            if (pipeline[name] is Connection) {
                pipeline.addBefore(name, "player_animator_packet_handler", cdh)
                break
            }
        }
    }

    override fun removePlayer(player: Player) {
        val connection = getConnection((player as CraftPlayer).handle)
        val channel: Channel = connection.channel
        channel.eventLoop().submit {
            channel.pipeline().remove("player_animator_packet_handler")
        }
    }

    override fun createRangeManager(entity: Entity): IRangeManager {
        return runCatching {
            val tracker = (entity as CraftEntity).handle.`moonrise$getTrackedEntity`()
            object : IRangeManager {
                override fun addPlayer(p0: Player) {
                    tracker.seenBy.add((p0 as CraftPlayer).handle.connection)
                }
                override fun removePlayer(p0: Player) {
                    tracker.seenBy.remove((p0 as CraftPlayer).handle.connection)
                }
                override fun setRenderDistance(p0: Int) {
                    runCatching {
                        RangeManager.range.set(tracker, p0)
                    }
                }
                override fun getPlayerInRange(): MutableSet<Player> = tracker.seenBy.map {
                    it.player.bukkitEntity
                }.toMutableSet()
            }
        }.getOrElse {
            val level: ServerLevel = (entity.world as org.bukkit.craftbukkit.CraftWorld).handle
            return RangeManager(level.chunkSource.chunkMap.entityMap.get(entity.entityId)!!)
        }
    }
}