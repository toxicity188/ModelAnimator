package kr.toxicity.animator.nms.v1_21_R3

import com.mojang.math.Transformation
import com.ticxo.playeranimator.api.model.player.LimbType
import com.ticxo.playeranimator.api.model.player.PlayerBone
import com.ticxo.playeranimator.api.nms.IRenderer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.*
import net.minecraft.world.entity.Display.ItemDisplay
import net.minecraft.world.item.ItemDisplayContext
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.EulerAngle
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


class DisplayRendererImpl(private val nmsImpl: NMSImpl): IRenderer {
    private class PlayerBoneData(val limb: PlayerBone) {
        val world: ServerLevel = (limb.model.base.world as CraftWorld).handle

        val cloud: AreaEffectCloud = AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, world)
        val display: ItemDisplay = ItemDisplay(EntityType.ITEM_DISPLAY, world)

        var translation = Vector3f()

        fun updateLocation() {
            val loc = limb.position.toLocation(limb.model.base.world).apply {
                y += 1.25
            }
            cloud.moveTo(loc.x, loc.y, loc.z)
            display.moveTo(loc.x, loc.y + display.getPassengerRidingPosition(cloud).y, loc.z, limb.model.baseYaw, 0F)
        }

        fun updateRotation() {
            val angle = if (!limb.type.isItem) limb.rotation
            else limb.rotation.add(PI / 2, 0.0, 0.0)
            display.setTransformation(Transformation(
                translation,
                angle.toQuaternion(),
                null,
                Quaternionf(0.0, 0.0, 0.0, 1.0)
            ))
        }

        fun EulerAngle.toQuaternion(): Quaternionf {
            val roll = x
            val pitch = y
            val yaw = z

            val cr: Double = cos(roll * 0.5)
            val sr: Double = sin(roll * 0.5)
            val cp: Double = cos(pitch * 0.5)
            val sp: Double = sin(pitch * 0.5)
            val cy: Double = cos(yaw * 0.5)
            val sy: Double = sin(yaw * 0.5)

            val q = Quaternionf()
            q.w = -(cr * cp * cy + sr * sp * sy).toFloat()
            q.x = -(sr * cp * cy - cr * sp * sy).toFloat()
            q.y = (cr * sp * cy + sr * cp * sy).toFloat()
            q.z = (cr * cp * sy - sr * sp * cy).toFloat()

            return q
        }
    }
    private var data: PlayerBoneData? = null

    private enum class AnimatorLimb(
        val modelId: Int,
        val slimId: Int,
        val initialTranslation: Vector3f
    ) {
        HEAD(1, 1, Vector3f(-0.35F, 0F, -0.35F)),

        RIGHT_ARM(2, 6, Vector3f(-0.35F, -512F, -0.35F)),
        RIGHT_FOREARM(2, 6, Vector3f(-0.35F, -1024F, -0.35F)),
        LEFT_ARM(3, 7, Vector3f(-0.35F, -1536F, -0.35F)),
        LEFT_FOREARM(3, 7, Vector3f(-0.35F, -2048F, -0.35F)),


        HIP(8, 8, Vector3f(-0.35F, -2560F, -0.35F)),
        WAIST(9, 9, Vector3f(-0.35F, -3072F, -0.35F)),
        CHEST(10, 10, Vector3f(-0.35F, -3584F, -0.35F)),

        RIGHT_LEG(4, 4, Vector3f(-0.35F, -4096F, -0.35F)),
        RIGHT_FORELEG(4, 4, Vector3f(-0.35F, -4608F, -0.35F)),
        LEFT_LEG(5, 5, Vector3f(-0.35F, -5120F, -0.35F)),
        LEFT_FORELEG(5, 5, Vector3f(-0.35F, -5632F, -0.35F)),

        RIGHT_ITEM(-1, -1, Vector3f(-0.35F, 0F, -0.35F)),
        LEFT_ITEM(-1, -1, Vector3f(-0.35F, 0F, -0.35F))
    }


    override fun setLimb(limb: PlayerBone) {
        val data = PlayerBoneData(limb).also {
            this.data = it
        }
        val display = data.display

        val cloud = data.cloud

        cloud.radius = 0.0f
        cloud.isInvisible = true
        display.entityData.set(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, 1)
        display.transformationInterpolationDelay = 1
        display.viewRange = 0.6F
        display.itemTransform = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
        display.startRiding(cloud)
        val animatorLimb = when (limb.type ?: LimbType.HEAD) {
            LimbType.HEAD -> AnimatorLimb.HEAD
            LimbType.HIP -> AnimatorLimb.HIP
            LimbType.WAIST -> AnimatorLimb.WAIST
            LimbType.CHEST -> AnimatorLimb.CHEST
            LimbType.RIGHT_ARM -> AnimatorLimb.RIGHT_ARM
            LimbType.RIGHT_FOREARM -> AnimatorLimb.RIGHT_FOREARM
            LimbType.LEFT_ARM -> AnimatorLimb.LEFT_ARM
            LimbType.LEFT_FOREARM -> AnimatorLimb.LEFT_FOREARM
            LimbType.RIGHT_LEG -> AnimatorLimb.RIGHT_LEG
            LimbType.RIGHT_FORELEG -> AnimatorLimb.RIGHT_FORELEG
            LimbType.LEFT_LEG -> AnimatorLimb.LEFT_LEG
            LimbType.LEFT_FORELEG -> AnimatorLimb.LEFT_FORELEG
            LimbType.RIGHT_ITEM -> AnimatorLimb.RIGHT_ITEM
            LimbType.LEFT_ITEM -> AnimatorLimb.LEFT_ITEM
        }
        data.translation = Vector3f(animatorLimb.initialTranslation)
        if (limb.type.isItem) {
            val var4 = limb.model.base as? LivingEntity ?: return

            if (limb.type == LimbType.RIGHT_ITEM) {
                display.itemStack = CraftItemStack.asNMSCopy(var4.equipment!!.itemInMainHand)
            } else {
                data.translation.x += 0.6F
                display.itemStack = CraftItemStack.asNMSCopy(var4.equipment!!.itemInOffHand)
            }
        } else {
            display.itemStack = CraftItemStack.asNMSCopy(nmsImpl.setSkullTexture(null, limb.model.texture).apply {
                itemMeta = itemMeta?.apply {
                    setCustomModelData(if (limb.model.texture.isSlim) animatorLimb.slimId else animatorLimb.modelId)
                }
            })
        }
    }

    private fun Player.sendPacket(packets: List<Packet<*>>) {
        val connection = (this as CraftPlayer).handle.connection
        packets.forEach {
            connection.send(it)
        }
    }
    private fun getSpawnPackets(): List<Packet<*>> {
        val data = data!!.apply {
            updateLocation()
        }
        fun Entity.addPacket() = ClientboundAddEntityPacket(
            id,
            uuid,
            x,
            y,
            z,
            xRot,
            yRot,
            type,
            0,
            deltaMovement,
            yHeadRot.toDouble()
        )
        return listOf(
            data.display.addPacket(),
            ClientboundSetEntityDataPacket(data.display.id, data.display.entityData.nonDefaultValues!!),
            data.cloud.addPacket(),
            ClientboundSetEntityDataPacket(data.cloud.id, data.cloud.entityData.nonDefaultValues!!),
            ClientboundSetPassengersPacket(data.cloud)
        )
    }
    private fun getDespawnPackets(): List<Packet<*>> {
        val data = data!!
        return listOf(ClientboundRemoveEntitiesPacket(data.display.id, data.cloud.id))
    }
    private fun getMovePackets(): List<Packet<*>> {
        val data = data!!.apply {
            updateLocation()
            updateRotation()
        }
        return listOf(
            ClientboundTeleportEntityPacket.teleport(data.cloud.id, PositionMoveRotation.of(data.cloud), emptySet(), data.cloud.onGround),
            ClientboundMoveEntityPacket.Rot(data.display.id, IRenderer.rotByte(data.limb.model.baseYaw), 0, false),
            ClientboundSetEntityDataPacket(data.display.id, data.display.entityData.nonDefaultValues!!)
        )
    }


    override fun spawn() {
        val packet = getSpawnPackets()
        data!!.limb.model.seenBy.forEach {
            it.sendPacket(packet)
        }
    }

    override fun spawn(p0: Player) {
        p0.sendPacket(getSpawnPackets())
    }

    override fun despawn() {
        val packet = getDespawnPackets()
        data!!.limb.model.seenBy.forEach {
            it.sendPacket(packet)
        }
    }

    override fun despawn(p0: Player) {
        p0.sendPacket(getDespawnPackets())
    }

    override fun update() {
        val packet = getMovePackets()
        data!!.limb.model.seenBy.forEach {
            it.sendPacket(packet)
        }
    }
}