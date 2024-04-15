package kr.toxicity.animator.util

import kr.toxicity.animator.api.ModelAnimator

val PLUGIN
    get() = ModelAnimator.inst()

fun info(message: String) = PLUGIN.logger.info(message)
fun warn(message: String) = PLUGIN.logger.warning(message)