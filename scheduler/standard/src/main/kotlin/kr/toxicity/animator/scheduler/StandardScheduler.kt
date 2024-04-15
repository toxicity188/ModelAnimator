package kr.toxicity.animator.scheduler

import kr.toxicity.animator.api.ModelAnimator
import kr.toxicity.animator.api.scheduler.AnimatorScheduler
import kr.toxicity.animator.api.scheduler.AnimatorTask
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

class StandardScheduler: AnimatorScheduler {
    private val plugin
        get() = ModelAnimator.inst()

    override fun task(runnable: Runnable): AnimatorTask {
        return wrap(Bukkit.getScheduler().runTask(plugin, runnable))
    }

    override fun asyncTask(runnable: Runnable): AnimatorTask {
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable))
    }

    override fun asyncTaskTimer(delay: Long, period: Long, runnable: Runnable): AnimatorTask {
        return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delay, period))
    }

    private fun wrap(task: BukkitTask) = object : AnimatorTask {
        override fun isCancelled(): Boolean = task.isCancelled
        override fun cancel() {
            task.cancel()
        }
    }
}