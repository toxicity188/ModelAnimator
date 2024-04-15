package kr.toxicity.animator.api.plugin;

import org.jetbrains.annotations.NotNull;

public record ReloadResult(@NotNull ReloadState state, long time) {
}
