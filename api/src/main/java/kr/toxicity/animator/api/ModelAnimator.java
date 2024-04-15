package kr.toxicity.animator.api;

import kr.toxicity.animator.api.animation.AnimationResult;
import kr.toxicity.animator.api.nms.NMS;
import kr.toxicity.animator.api.plugin.ReloadResult;
import kr.toxicity.animator.api.scheduler.AnimatorScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class ModelAnimator extends JavaPlugin {
    private static ModelAnimator instance;
    @Override
    public void onLoad() {
        if (instance != null) throw new RuntimeException();
        instance = this;
    }
    public static @NotNull ModelAnimator inst() {
        return Objects.requireNonNull(instance);
    }

    public abstract @NotNull AnimatorScheduler scheduler();
    public abstract @NotNull NMS nms();
    public abstract @NotNull AnimationResult animate(@NotNull Player player, @NotNull String animation);
    public abstract @NotNull ReloadResult reload();
    public abstract boolean onReload();
}
