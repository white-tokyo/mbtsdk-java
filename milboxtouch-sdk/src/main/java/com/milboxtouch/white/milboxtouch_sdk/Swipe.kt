package com.milboxtouch.white.milboxtouch_sdk

/**
 * Created by moajo on 2016/09/15.
 */
class Swipe(private val startAnglePosition: Angle, private val endAnglePosition: Angle, private val timeDurationMilisec: Long) {

    val speed: Float
        get() = (endAnglePosition - startAnglePosition).abs / timeDurationMilisec*1000
    val direction: SwipeDirection
        get() {
            val s = startAnglePosition.angleBlock
            val e = endAnglePosition.angleBlock
            when (s) {
                AngleBlock.UpperLeft ->
                    if (e == AngleBlock.UpperRight) {
                        return SwipeDirection.RIGHT
                    } else if (e == AngleBlock.LowerLeft) {
                        return SwipeDirection.DOWN
                    }
                AngleBlock.UpperRight ->
                    if (e == AngleBlock.UpperLeft) {
                        return SwipeDirection.LEFT
                    } else if (e == AngleBlock.LowerRight) {
                        return SwipeDirection.DOWN
                    }
                AngleBlock.LowerLeft ->
                    if (e == AngleBlock.UpperLeft) {
                        return SwipeDirection.UP
                    } else if (e == AngleBlock.LowerRight) {
                        return SwipeDirection.RIGHT
                    }
                AngleBlock.LowerRight ->
                    if (e == AngleBlock.UpperRight) {
                        return SwipeDirection.UP
                    } else if (e == AngleBlock.LowerLeft) {
                        return SwipeDirection.LEFT
                    }
            }
            return if (startAnglePosition.value > endAnglePosition.value) SwipeDirection.RIGHT else SwipeDirection.LEFT
        }


}