package com.milboxtouch.white.milboxtouch_sdk

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.os.Debug
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import rx.Observable
import rx.Subscription
import rx.schedulers.Timestamped
import rx.subjects.PublishSubject
import rx.subjects.Subject
import rx.subscriptions.CompositeSubscription
import rx.subscriptions.Subscriptions
import java.util.concurrent.TimeUnit

fun <T> Observable<T>.pre(): Observable<Pair<T?, T>> {
    return this.scan(Pair<T?, T?>(null, null), { pre, current -> Pair(pre.second, current) }).skip(1).map { Pair(it.first, it.second!!) }
}

/**
 * Created by moajo on 2016/09/14.
 */
class MilboxView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : View(context, attrs, defStyle) {

    //parameters
    /**
     * セットアップの進行を許容する誤差。小さすぎるとタップで進行してしまう。大きすぎると進行しない。変更する必要ない？
     */
    var setupProgressTolerance = 20
    /**
     * セットアップステージ数。小さいとすぐセットアップが終わるが精度が悪くなるかも。大きいと時間が掛かるが精度が良くなる。
     */
    var setupStageCount = 10
    /**
     * セットアップ許容誤差。小さいとなかなか終わらない。大きいと精度が悪くなる。
     */
    var setupTolerance = 5
    /**
     * タップを検出する許容時間。小さいとすばやく押さないとタップにならない。大きいと長押しでもタップになる。
     */
    var tapDetectDuration = 1f
    var tapDetectTolerance = 15f

    //observables
    /**
     * セットアップの完了を観測する
     */
    val onSetupCompletedObservable: Observable<Void>
        get() = onSetupCompletedSubject.asObservable()
    /**
     * セットアップの進行を観測する
     */
    val onSetupProgressObservable: Observable<Void>
        get() = onSetupProgressSubject.asObservable()


    //subjects
    private val touchBeganSubject: PublishSubject<Float> = PublishSubject.create()
    private val touchMovedSubject: PublishSubject<Float> = PublishSubject.create()
    private val touchEndedSubject: PublishSubject<Float> = PublishSubject.create()

    private val onSetupCompletedSubject: PublishSubject<Void> = PublishSubject.create()
    private val onSetupProgressSubject: PublishSubject<Void> = PublishSubject.create()
    private val onTapSubject: PublishSubject<Float> = PublishSubject.create()

    //listeners
    private val onSetupProgressListeners: MutableSet<() -> Void> = mutableSetOf()
    private val onSetupCompletedListeners: MutableSet<() -> Void> = mutableSetOf()
    private val tapListeners: MutableSet<() -> Void> = mutableSetOf()
    private val doubleTapListeners: MutableSet<() -> Void> = mutableSetOf()

    private val subscriptions: CompositeSubscription = CompositeSubscription()

    //events
    fun addOnTapListener(listener: () -> Void) {
        tapListeners.add(listener)
    }

    fun addOnDoubleTapListener(listener: () -> Void) {

    }

    fun setOnScrollListener(listener: (degree: Float) -> Void) {

    }

    fun setOnScrollBeganListener(listener: () -> Void) {

    }

    fun setOnScrollEndedListener(listener: () -> Void) {

    }

    fun addOnSwipeListener(listener: (speed: Float, direction: SwipeDirection) -> Void) {

    }

    fun addOnSetpuProgressListener(listener: () -> Void) {

    }

    fun addOnSetpuCompletedListener(listener: () -> Void) {

    }

    init {
        // init touch listener
        isFocusable = true
        isFocusableInTouchMode = true
        setOnTouchListener { arg0, event ->
            val x = event.x

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.e("aaa","down")
                    touch_start(x)
                }
                MotionEvent.ACTION_MOVE -> {
                    Log.e("aaa","move")
                    touch_move(x)
                }
                MotionEvent.ACTION_UP -> {
                    Log.e("aaa","up")
                    touch_up(x)
                }
            }
            true
        }
    }

    fun startSetup() {
        Log.e("aaa", "setup start!")
        //init subscription
        onSetupProgressSubject.subscribe {
            onSetupProgressListeners.forEach { it() }
        }.autoUnSubscribe()
        onSetupCompletedSubject.subscribe {
            onSetupCompletedListeners.forEach { it() }
        }.autoUnSubscribe()

        val angleFactory = Angle.Factory(900f, 1600f)

        //setup subscribe
        val setupComplete = Observable.merge(touchBeganSubject, touchMovedSubject, touchEndedSubject).timestamp().buffer(2, 1).doOnNext {
            Log.e("aaa", "timeDelta: ${it[1].timestampMillis - it[0].timestampMillis} ms")
        }.buffer(30, 1).map { it.map { it[1].timestampMillis - it[0].timestampMillis }.average() }.doOnNext {
            Log.e("aaa", "average: ${it} ms")//過去３０このイベントの時間感覚の平均が20以下になることが10回あれば終了。
        }.filter { it < 20 }.take(10).count().doOnNext {
            Log.e("aaa", "what ? :$it")
        }
        val last100 = Observable.merge(touchBeganSubject, touchMovedSubject, touchEndedSubject).takeUntil(setupComplete).takeLast(100).publish()
        val r = last100.reduce { fl1: Float, fl2: Float -> Math.max(fl1, fl2) }
        val l = last100.reduce { fl1: Float, fl2: Float -> Math.min(fl1, fl2) }
        Observable.zip(r, l, { right, left -> Pair(right, left) }).subscribe {
            angleFactory.rightBounds = it.first//終了時の最後100個の最大と最小を取って境界とする。
            angleFactory.leftBounds = it.second
            Log.e("aaa", "complete!! left: ${angleFactory.leftBounds} right: ${angleFactory.rightBounds}")
            onSetupCompletedSubject.onNext(null)
        }
        last100.connect()

//        touchBeganSubject.zipWith(touchEndedSubject, { b, e -> Pair(b, e) })
//                .filter { Math.abs(it.first - it.second) > setupProgressTolerance }//タップで進行しないようにする
//                .scan(listOf<Pair<Float, Float>>(), { pre, current ->
//                    val recent = pre.toMutableList()
//                    recent.add(current)
//                    if (recent.count() > setupStageCount) {
//                        recent.removeAt(0)
//                    }
//                    onSetupProgressSubject.onNext(null)
//                    recent
//                })//最近のを取る
//                .filter { it.count() == setupStageCount }//揃うまで待つ
//                .map { recent ->
//                    angleFactory.leftBounds = recent.map { it.first }.max()!!
//                    val leftMin = recent.map { it.first }.min()!!
//                    angleFactory.rightBounds = recent.map { it.second }.max()!!
//                    val rightMin = recent.map { it.second }.min()!!
//                    Log.e("aaa", "left: ${angleFactory.leftBounds - leftMin} right: ${angleFactory.rightBounds - rightMin}")
//                    angleFactory.leftBounds - leftMin < setupTolerance && angleFactory.rightBounds - rightMin < setupTolerance//それぞれの最大最小の差が一定いないか判定
//                }.filter { it }.subscribe {
//            Log.e("aaa", "complete!!")
//            onSetupCompletedSubject.onNext(null)
//        }.autoUnSubscribe()

        touchBeganSubject.subscribe { Log.e("aaaa","BEGAN!!!") }
        val detectAngles = Observable.merge(touchBeganSubject.map { Pair("began", it) }, touchMovedSubject.map { Pair("move", it) }, touchEndedSubject.map { Pair("ended", it) }).skipUntil(onSetupCompletedSubject).map {
            Pair(it.first, angleFactory.toAngle(it.second))
        }.publish()
        val sequence = detectAngles.takeUntil(detectAngles.throttleLast(50, TimeUnit.MILLISECONDS).filter { it.first == "ended" })
        detectAngles.connect().autoUnSubscribe()
        sequence.doOnCompleted {
//            Log.e("aaa", "sequence end!!!!!!!!!!!")
        }.repeat().subscribe {
//            Log.e("aaa", "sequence")
        }

        val seq_began = sequence.map { it.second }.pre().filter { it.first==null }.map{it.second}.repeat().publish().apply { connect().autoUnSubscribe() }
        val seq_move = sequence.skip(1).map { it.second }.repeat().publish().apply { connect().autoUnSubscribe() }
        val seq_ended = sequence.last().map { it.second }.repeat().publish().apply { connect().autoUnSubscribe() }

        seq_began.subscribe {
            Log.e("aaa","BEGAN")
        }
        seq_move.subscribe {
            Log.e("aaa","MOVE")
        }
        seq_ended.subscribe {
            Log.e("aaa","END")
        }

        val tap = sequence.first().timestamp().zipWith(sequence.last().timestamp(), { first, last -> Pair(first, last) }).repeat().doOnNext {
            Log.e("aaa","time: ${it.second.timestampMillis - it.first.timestampMillis}")
            Log.e("aaa","delta: ${(it.second.value.second - it.first.value.second).abs}")
        }
                .filter { it.second.timestampMillis - it.first.timestampMillis < 200 }//時間制限
                .filter { (it.second.value.second - it.first.value.second).abs < 30 }//移動距離制限
                .map { it.second }

        tap.pre().map {
            if (it.second.timestampMillis - (it.first?.timestampMillis ?: 0L) < 500L) "doubleTap" else "tap"
        }.subscribe {
            Log.e("aaa", it)
        }

        val moveDelta = seq_move.pre().map { Pair(it.second, it.second - (it.first ?: it.second)) }//move,moveDelta
        val scrollStart = moveDelta.map { it.second.abs }.filter { deltaScale -> 0.3 < deltaScale && deltaScale < 30 }
        val scrollStroke = scrollStart.takeUntil(touchEndedSubject)
        scrollStroke.take(1).doOnNext {
            Log.e("aaa","scrollBegan")
        }.zipWith(scrollStroke.count(), { a, b -> Pair(a, b) }).repeat().subscribe()
        scrollStroke.count().repeat().subscribe {
            if (it != 0) {
                Log.e("aaa","scrollEnded")
            }
        }
        scrollStroke.repeat().subscribe {
            Log.e("aaa","scroll")
        }

        val ts = seq_began.timestamp().first().concatWith(seq_move.timestamp()).scan(listOf<Timestamped<Angle>>(), { pre, current ->
            val recent = pre.toMutableList()
            recent.add(current)
            recent
        })
        val touchStroke = Observable.combineLatest(ts, seq_ended.timestamp(), { list, end ->
            val a = list.toMutableList()
            a.add(end)
            a
        }).first()
//        val touchStroke = detectTouchBegan.first().concatWith(detectTouchMoved).takeUntil(detectTouchEnded)
        touchStroke
//                .timestamp().toList()
                .repeat().subscribe {
            Log.e("aaa", "strList count:${it.count()} f:${it.first().value} l:${it.last().value}")
            val first = it.first()
            val last = it.last()
            val startAngle = last.value
            val endAngle = first.value
            val angleDelta = (startAngle - endAngle).abs
            val timeDelta = last.timestampMillis - first.timestampMillis
            Log.e("aaa", "tD:$timeDelta s:${startAngle.value} e:${endAngle.value}")

            val positionCondition = angleDelta > tapDetectTolerance
            val timeCondition = timeDelta < tapDetectDuration
            if (positionCondition && timeCondition) {//swipe
                val swipe = Swipe(startAngle, endAngle, timeDelta)
                Log.e("aaa", "swipe speed:${swipe.speed} direction: ${swipe.direction}")
            }
        }


    }

    private fun touch_start(x: Float) {
//        Log.e("tag", "start x:$x")
        touchBeganSubject.onNext(x)
    }

    private fun touch_move(x: Float) {
//        Log.e("tag", "move")
        touchMovedSubject.onNext(x)
    }

    private fun touch_up(x: Float) {
//        Log.e("tag", "end")
        touchEndedSubject.onNext(x)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscriptions.unsubscribe()
    }

    private fun Subscription.autoUnSubscribe() {
        subscriptions.add(this)
    }
}
