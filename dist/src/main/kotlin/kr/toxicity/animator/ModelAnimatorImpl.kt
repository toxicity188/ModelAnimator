package kr.toxicity.animator

import com.ticxo.playeranimator.api.PlayerAnimator
import com.ticxo.playeranimator.api.PlayerAnimatorPlugin
import com.ticxo.playeranimator.api.animation.AnimationManager
import com.ticxo.playeranimator.api.model.ModelManager
import com.ticxo.playeranimator.api.model.player.PlayerModel
import com.ticxo.playeranimator.api.nms.INMSHandler
import kr.toxicity.animator.api.ModelAnimator
import kr.toxicity.animator.api.animation.AnimationResult
import kr.toxicity.animator.api.nms.NMS
import kr.toxicity.animator.api.plugin.ReloadResult
import kr.toxicity.animator.api.plugin.ReloadState
import kr.toxicity.animator.api.scheduler.AnimatorScheduler
import kr.toxicity.animator.api.scheduler.AnimatorTask
import kr.toxicity.animator.scheduler.FoliaScheduler
import kr.toxicity.animator.scheduler.StandardScheduler
import kr.toxicity.animator.util.info
import kr.toxicity.animator.util.warn
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

@Suppress("UNUSED")
class ModelAnimatorImpl: ModelAnimator() {
    private lateinit var animator: PlayerAnimator
    private lateinit var nms: NMS
    private val scheduler = runCatching {
        Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler")
        FoliaScheduler()
    }.getOrElse {
        StandardScheduler()
    }
    private var onReload = false

    override fun onEnable() {
        nms = when (val version = Bukkit.getServer()::class.java.`package`.name.split('.')[3]) {
            "v1_20_R3" -> kr.toxicity.animator.nms.v1_20_R3.NMSImpl()
            else -> {
                warn("Unsupported version found: $version")
                warn("Plugin will automatically disabled.")
                Bukkit.getPluginManager().disablePlugin(this)
                return
            }
        }
        animator = object : PlayerAnimator() {
            override fun injectPlayer(p0: Player) {
                nms.injectPlayer(p0)
            }

            override fun removePlayer(p0: Player) {
                nms.removePlayer(p0)
            }
        }.apply {
            animationManager = AnimationManager()
            modelManager = object : ModelManager() {

                private val modelMap = ConcurrentHashMap<UUID, PlayerModel>()
                private var task: AnimatorTask? = null

                override fun getPlayerModel(entity: Entity): PlayerModel? {
                    return modelMap[entity.uniqueId]
                }

                override fun activate() {
                    task?.cancel()
                    task = scheduler.asyncTaskTimer(1, 1) {
                        val iterator = modelMap.values.iterator()
                        while (iterator.hasNext()) {
                            val model = iterator.next()
                            if (!model.update()) {
                                model.despawn()
                                iterator.remove()
                            }
                        }
                    }
                }

                override fun registerModel(model: PlayerModel) {
                    modelMap[model.base.uniqueId] = model
                }

                override fun unregisterModel(entity: Entity) {
                    modelMap.remove(entity.uniqueId)
                }
            }.apply {
                activate()
            }
        }
        PlayerAnimatorPlugin.plugin = this
        animator.nms = nms as INMSHandler
        PlayerAnimator.api = animator
        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler
            fun quit(e: PlayerQuitEvent) {
                playerMap.remove(e.player.uniqueId)?.let {
                    e.player.isInvisible = false
                    it.cancel()
                }
            }
        }, this)
        getCommand("modelanimator")?.setExecutor(object : TabExecutor {
            override fun onCommand(p0: CommandSender, p1: Command, p2: String, p3: Array<out String>): Boolean {
                when (if (p3.isEmpty()) "help" else p3[0]) {
                    "reload" -> {
                        if (p0.hasPermission("modelanimator.reload")) {
                            scheduler.asyncTask {
                                val reload = reload()
                                when (reload.state) {
                                    ReloadState.SUCCESS -> p0.sendMessage("Reload success: (${reload.time} ms)")
                                    ReloadState.FAIL -> p0.sendMessage("Reload failed.")
                                    ReloadState.ON_RELOAD -> p0.sendMessage("Still on reload.")
                                }
                            }
                        } else p0.sendMessage("You have no permission.")
                    }
                    "play" -> {
                        val player = (p0 as? Player) ?: run {
                            p0.sendMessage("You are not player.")
                            return true
                        }
                        if (p3.size < 2) {
                            p0.sendMessage("/ma play <animation>")
                            return true
                        }
                        if (p0.hasPermission("modelanimator.play")) {
                            when (animate(player, "animator.${p3[1]}")) {
                                AnimationResult.SUCCESS -> p0.sendMessage("Successfully played.")
                                AnimationResult.FAIL -> p0.sendMessage("This animation doesn't exist: ${p3[1]}")
                            }
                        } else p0.sendMessage("You have no permission.")
                    }
                    else -> {
                        p0.sendMessage("/ma reload")
                        p0.sendMessage("/ma play <animation>")
                    }
                }
                return true
            }

            override fun onTabComplete(
                p0: CommandSender,
                p1: Command,
                p2: String,
                p3: Array<out String>
            ): List<String>? {
                return when (p3.size) {
                    1 -> listOf("reload", "play")
                    2 -> ArrayList<String>().apply {
                        animator.animationManager.registry.forEach {
                            val key = it.key.substringAfterLast(':')
                            it.value.animations.keys.forEach { s ->
                                add("$key.${s}")
                            }
                        }
                    }
                    else -> null
                }
            }
        })
        scheduler.asyncTask {
            reload()
            info("Plugin enabled.")
        }
    }

    override fun onDisable() {
        info("Plugin disabled.")
    }

    override fun scheduler(): AnimatorScheduler = scheduler
    override fun nms(): NMS = nms

    private val playerMap = HashMap<UUID,CancellablePlayerModel>()
    abstract class CancellablePlayerModel(player: Player): PlayerModel(player) {
        abstract fun cancel()
    }
    override fun animate(player: Player, animation: String): AnimationResult = runCatching {
        val uuid = player.uniqueId
        val hand = player.inventory.itemInMainHand
        val offhand = player.inventory.itemInOffHand
        val targetPlayer = player.world.getNearbyEntities(player.location, 32.0, 32.0, 32.0).mapNotNull {
            it as? Player
        }
        playerMap.put(uuid, object : CancellablePlayerModel(player) {
            override fun spawn() {
                targetPlayer.forEach {
                    spawn(it)
                }
            }
            override fun despawn() {
                targetPlayer.forEach {
                    despawn(it)
                }
                nms.sendItemChange(
                    player,
                    mapOf(
                        EquipmentSlot.HAND to hand,
                        EquipmentSlot.OFF_HAND to offhand
                    ),
                    targetPlayer
                )
                player.updateInventory()
                player.isInvisible = false
                playerMap.remove(uuid)
            }
            override fun cancel() {
                targetPlayer.forEach {
                    despawn(it)
                }
            }
        }.apply {
            playAnimation(animation)
        })?.cancel()
        scheduler.asyncTask {
            player.isInvisible = true
            val air = ItemStack(Material.AIR)
            nms.sendItemChange(
                player,
                mapOf(
                    EquipmentSlot.HAND to air,
                    EquipmentSlot.OFF_HAND to air
                ),
                targetPlayer
            )
        }
        AnimationResult.SUCCESS
    }.getOrElse {
        it.printStackTrace()
        AnimationResult.FAIL
    }

    override fun reload(): ReloadResult {
        if (onReload) return ReloadResult(ReloadState.ON_RELOAD, 0)
        onReload = true
        val time = System.currentTimeMillis()
        return runCatching {
            val data = dataFolder.apply {
                if (!exists()) mkdir()
            }
            loadAssets("pack", File(data,"build").apply {
                deleteRecursively()
                mkdir()
            })
            animator.animationManager.clearRegistry()
            File(data, "packs").apply {
                if (!exists()) mkdir()
                val steve = File(this, "steve.bbmodel")
                if (!steve.exists()) getResource("steve.bbmodel")?.buffered()?.use { copy ->
                    steve.outputStream().buffered().use { os ->
                        copy.copyTo(os)
                    }
                }
            }.listFiles()?.forEach { file ->
                if (file.extension == "bbmodel") animator.animationManager.importAnimations("animator", file)
            }
            onReload = false
            ReloadResult(ReloadState.SUCCESS, System.currentTimeMillis() - time)
        }.getOrElse { e ->
            warn("Failed to reload.")
            warn("Reason: ${e.message}")
            onReload = false
            ReloadResult(ReloadState.FAIL, System.currentTimeMillis() - time)
        }
    }
    override fun onReload(): Boolean = onReload

    private fun loadAssets(prefix: String, dir: File) {
        JarFile(file).use {
            it.entries().asSequence().sortedBy { dir ->
                dir.name.length
            }.forEach { entry ->
                if (!entry.name.startsWith(prefix)) return@forEach
                if (entry.name.length <= prefix.length + 1) return@forEach
                val name = entry.name.substring(prefix.length + 1)
                val file = File(dir, name)
                if (entry.isDirectory) {
                    if (!file.exists()) file.mkdir()
                } else {
                    getResource(entry.name)?.buffered()?.use { stream ->
                        if (!file.exists()) {
                            file.createNewFile()
                            file.outputStream().buffered().use { fos ->
                                stream.copyTo(fos)
                            }
                        }
                    }
                }
            }
        }
    }
}