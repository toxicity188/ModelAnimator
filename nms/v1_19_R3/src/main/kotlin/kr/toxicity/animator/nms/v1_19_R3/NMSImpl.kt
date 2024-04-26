package kr.toxicity.animator.nms.v1_19_R3

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.datafixers.util.Pair
import com.ticxo.playeranimator.api.nms.IRangeManager
import com.ticxo.playeranimator.api.nms.IRenderer
import com.ticxo.playeranimator.api.texture.TextureWrapper
import kr.toxicity.animator.api.nms.NMS
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.behavior.EntityTracker
import org.bukkit.Material
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftEntity
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class NMSImpl: NMS, com.ticxo.playeranimator.nms.v1_19_R3.NMSHandler_v1_19_R3() {
    override fun createRenderer(): IRenderer = DisplayRendererImpl(this)
    override fun getTexture(player: Player): String {
        return (player as CraftPlayer).handle.gameProfile.properties.get("textures").first().value
    }

    override fun setSkullTexture(skull: ItemStack?, texture: TextureWrapper): ItemStack {
        return CraftItemStack.asBukkitCopy(CraftItemStack.asNMSCopy(skull ?: ItemStack(Material.PLAYER_HEAD)).apply {
            addTagElement("SkullOwner", NbtUtils.writeGameProfile(CompoundTag(), GameProfile(UUID.randomUUID(), "").apply {
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
                        org.bukkit.inventory.EquipmentSlot.HEAD -> EquipmentSlot.HEAD
                        org.bukkit.inventory.EquipmentSlot.CHEST -> EquipmentSlot.CHEST
                        org.bukkit.inventory.EquipmentSlot.LEGS -> EquipmentSlot.LEGS
                        org.bukkit.inventory.EquipmentSlot.FEET -> EquipmentSlot.FEET
                        org.bukkit.inventory.EquipmentSlot.HAND -> EquipmentSlot.MAINHAND
                        org.bukkit.inventory.EquipmentSlot.OFF_HAND -> EquipmentSlot.OFFHAND
                    },
                    CraftItemStack.asNMSCopy(it.value)
                )
            }
        )
        targetPlayers.forEach {
            (it as CraftPlayer).handle.connection.send(packet)
        }
    }

    override fun createRangeManager(entity: Entity): IRangeManager {
        return runCatching {
            val tracker = (entity as CraftEntity).handle.tracker!!
            object : IRangeManager {
                override fun addPlayer(p0: Player) {
                    tracker.seenBy.add((p0 as CraftPlayer).handle.connection)
                }
                override fun removePlayer(p0: Player) {
                    tracker.seenBy.remove((p0 as CraftPlayer).handle.connection)
                }
                override fun setRenderDistance(p0: Int) {
                    runCatching {
                        EntityTracker::class.java.getDeclaredField("d").run {
                            isAccessible = true
                            set(tracker, p0)
                        }
                    }
                }
                override fun getPlayerInRange(): MutableSet<Player> = tracker.seenBy.map {
                    it.player.bukkitEntity
                }.toMutableSet()
            }
        }.getOrElse {
            super.createRangeManager(entity)
        }
    }
}