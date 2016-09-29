package com.milboxtouch.white.milboxtouch_sdk

/**
 * Created by moajo on 2016/09/15.
 */
class Angle {
    val value: Float

    private constructor(angle: Float) {
        value = if (angle >= 360) angle - 360 else angle
    }

    val abs: Float
        get() = Math.abs(value)
    val value180: Float
        get() = if (value > 180) {
            360 - value
        } else {
            value
        }


    val angleBlock: AngleBlock
        get() = if (0 <= value && value < 90) {
            AngleBlock.UpperRight
        } else if (90 <= value && value < 180) {
            AngleBlock.UpperLeft
        } else if (180 <= value && value < 270) {
            AngleBlock.LowerLeft
        } else {
            AngleBlock.LowerRight
        }


    operator fun plus(other: Angle): Angle {
        return Angle(other.value + value)
    }

    operator fun minus(other: Angle): Angle {
        val v = value - other.value
        return Angle(if (v < 0) v + 360 else v)
    }

    class Factory(var leftBounds: Float, var rightBounds: Float) {
        fun toAngle(pos: Float): Angle {
            val correctionPos = if (pos < leftBounds) {
                leftBounds
            } else if (rightBounds < pos) {
                rightBounds
            } else {
                pos
            }
            val dir = correctionPos - leftBounds
            val limit = rightBounds - leftBounds
            val rate = dir / limit
            val correction = 170
            val angle = rate * 360 + correction
            return Angle(angle)
        }

        fun toAngleDelta(delta: Float): Angle {
            val limit = rightBounds - leftBounds
            val rate = delta / limit
            val angle = rate * 360
            return Angle(angle)
        }
    }
}