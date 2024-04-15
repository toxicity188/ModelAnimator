package kr.toxicity.animator.api.scheduler;

public interface AnimatorTask {
    boolean isCancelled();
    void cancel();
}
