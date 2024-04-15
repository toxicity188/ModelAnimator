package kr.toxicity.animator.api.scheduler;

import org.jetbrains.annotations.NotNull;

public interface AnimatorScheduler {
    @NotNull AnimatorTask task(@NotNull Runnable runnable);
    @NotNull AnimatorTask asyncTask(@NotNull Runnable runnable);
    @NotNull AnimatorTask asyncTaskTimer(long delay, long period, @NotNull Runnable runnable);
}
