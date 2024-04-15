package kr.toxicity.animator.scheduler

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import kr.toxicity.animator.api.ModelAnimator
import kr.toxicity.animator.api.scheduler.AnimatorScheduler
import kr.toxicity.animator.api.scheduler.AnimatorTask
import org.bukkit.Bukkit
import java.util.concurrent.TimeUnit

class FoliaScheduler: AnimatorScheduler {
    private val plugin
        get() = ModelAnimator.inst()

    override fun task(runnable: Runnable): AnimatorTask {
        return wrap(Bukkit.getGlobalRegionScheduler().run(plugin) {
            runnable.run()
        })
    }

    override fun asyncTask(runnable: Runnable): AnimatorTask {
        return wrap(Bukkit.getAsyncScheduler().runNow(plugin) {
            runnable.run()
        })
    }

    override fun asyncTaskTimer(delay: Long, period: Long, runnable: Runnable): AnimatorTask {
        return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, {
            runnable.run()
        }, delay * 50, period * 50, TimeUnit.MILLISECONDS))
    }

    private fun wrap(task: ScheduledTask) = object : AnimatorTask {
        override fun isCancelled(): Boolean = task.isCancelled
        override fun cancel() {
            task.cancel()
        }
    }
}