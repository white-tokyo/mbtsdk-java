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

/**
 * 直前の値とのペアを取る。最初の値はnullとペア。
 */
fun <T> Observable<T>.pre(): Observable<Pair<T?, T>> {
    return this.scan(Pair<T?, T?>(null, null), { pre, current -> Pair(pre.second, current) }).skip(1).map { Pair(it.first, it.second!!) }
}

/**
 * 各要素までの累積リストにする。
 */
fun <T> Observable<T>.list():Observable<List<T>>{
    return this.scan(listOf<T>(),{list,current->list.toMutableList().apply {
        add(current)
    }})
}

/**
 * Created by moajo on 2016/09/14.
 */
class MilboxView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : View(context, attrs, defStyle) {

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
    var tapDetectDuration = 450f
    var tapDetectTolerance = 30f

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
    val onTapObservable: Observable<Void>
    get() = onTapSubject.asObservable()
    val onDoubleTapObservable: Observable<Void>
        get() = onDoubleTapSubject.asObservable()
    /**
     * スクロール角の差分が通知される
     */
    val onScrollObservable:Observable<Float>
        get()=onScrollSubject.asObservable()
    val onScrollBeganObservable:Observable<Void>
        get()=onScrollBeganSubject.asObservable()
    val onScrollEndedObservable:Observable<Void>
        get()=onScrollEndedSubject.asObservable()
    val onSwipeObservable: Observable<Swipe>
        get()=onSwipeSubject.asObservable()


    //subjects
    private val touchBeganSubject: PublishSubject<Float> = PublishSubject.create()
    private val touchMovedSubject: PublishSubject<Float> = PublishSubject.create()
    private val touchEndedSubject: PublishSubject<Float> = PublishSubject.create()

    private val onSetupCompletedSubject: PublishSubject<Void> = PublishSubject.create()
    private val onSetupProgressSubject: PublishSubject<Void> = PublishSubject.create()
    private val onTapSubject: PublishSubject<Void> = PublishSubject.create()
    private val onDoubleTapSubject: PublishSubject<Void> = PublishSubject.create()
    private val onScrollBeganSubject: PublishSubject<Void> = PublishSubject.create()
    private val onScrollSubject: PublishSubject<Float> = PublishSubject.create()
    private val onScrollEndedSubject: PublishSubject<Void> = PublishSubject.create()
    private val onSwipeSubject: PublishSubject<Swipe> = PublishSubject.create()

    //listeners
    private val onSetupProgressListeners: MutableSet<() -> Void> = mutableSetOf()
    private val onSetupCompletedListeners: MutableSet<() -> Void> = mutableSetOf()
    private val onTapListeners: MutableSet<() -> Void> = mutableSetOf()
    private val onDoubleTapListeners: MutableSet<() -> Void> = mutableSetOf()
    private val onSwipeListeners: MutableSet<(Swipe) -> Void> = mutableSetOf()
    private val onScrollListeners: MutableSet<(Float) -> Void> = mutableSetOf()
    private val onScrollEndedListeners: MutableSet<() -> Void> = mutableSetOf()
    private val onScrollBeganListeners: MutableSet<() -> Void> = mutableSetOf()

    private val subscriptions: CompositeSubscription = CompositeSubscription()

    //events
    fun addOnSetupProgressListener(listener: () -> Void) {onSetupProgressListeners.add(listener) }
    fun removeOnSetupProgressListener(listener: () -> Void) {onSetupProgressListeners.remove(listener) }
    fun addOnSetupCompletedListener(listener: () -> Void) {onSetupCompletedListeners.add(listener) }
    fun removeOnSetupCompletedListener(listener: () -> Void) {onSetupCompletedListeners.remove(listener) }
    fun addOnTapListener(listener: () -> Void) { onTapListeners.add(listener) }
    fun removeOnTapListener(listener: () -> Void) { onTapListeners.remove(listener) }
    fun addOnDoubleTapListener(listener: () -> Void) { onDoubleTapListeners.add(listener) }
    fun removeOnDoubleTapListener(listener: () -> Void) { onDoubleTapListeners.remove(listener) }
    fun addOnScrollListener(listener: (degree: Float) -> Void) {onScrollListeners.add(listener) }
    fun removeOnScrollListener(listener: (degree: Float) -> Void) {onScrollListeners.remove(listener) }
    fun addOnScrollBeganListener(listener: () -> Void) { onScrollBeganListeners.add(listener)}
    fun removeOnScrollBeganListener(listener: () -> Void) { onScrollBeganListeners.remove(listener)}
    fun addOnScrollEndedListener(listener: () -> Void) { onScrollEndedListeners.add(listener)}
    fun removeOnScrollEndedListener(listener: () -> Void) { onScrollEndedListeners.remove(listener)}
    fun addOnSwipeListener(listener: (Swipe) -> Void) { onSwipeListeners.add(listener)}
    fun removeOnSwipeListener(listener: (Swipe) -> Void) { onSwipeListeners.remove(listener)}



    init {
        // init touch listener
        isFocusable = true
        isFocusableInTouchMode = true
        setOnTouchListener { arg0, event ->
            val x = event.x
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchBeganSubject.onNext(x)
                }
                MotionEvent.ACTION_MOVE -> {
                    touchMovedSubject.onNext(x)
                }
                MotionEvent.ACTION_UP -> {
                    touchEndedSubject.onNext(x)
                }
            }
            true
        }
    }

    fun startSetup() {
        subscriptions.clear()

        //init subscription
        onSetupProgressSubject.subscribe { onSetupProgressListeners.forEach { it() }}.autoUnSubscribe()
        onSetupCompletedSubject.subscribe { onSetupCompletedListeners.forEach { it() } }.autoUnSubscribe()
        onTapSubject.subscribe{onTapListeners.forEach { it() }}.autoUnSubscribe()
        onDoubleTapSubject.subscribe { onDoubleTapListeners.forEach { it() } }.autoUnSubscribe()
        onScrollSubject.subscribe {delta-> onScrollListeners.forEach { it(delta) } }.autoUnSubscribe()
        onScrollBeganSubject.subscribe { onScrollBeganListeners.forEach { it() } }.autoUnSubscribe()
        onScrollEndedSubject.subscribe { onScrollEndedListeners.forEach { it() } }.autoUnSubscribe()
        onSwipeSubject.subscribe {swipe-> onSwipeListeners.forEach { it(swipe) } }.autoUnSubscribe()



        val angleFactory = Angle.Factory(900f, 1600f)

        //setup subscribe
        val upOrDown = Observable.merge(touchBeganSubject,touchEndedSubject).throttleLast(250,TimeUnit.MILLISECONDS).doOnNext {
            Log.e("aaa","upOrDowns: ${it}")
        }
        val moveOverBounds = touchMovedSubject.buffer(2,1).filter { Math.abs(it[0]-it[1])>100 }.flatMap { Observable.just(it[0],it[1]) }.doOnNext {
            Log.e("aaa","moveOverBounds: $it")
        }
        val setupSignal = Observable.merge(upOrDown,moveOverBounds)
        val average = setupSignal.list().map { list->list.average() }
        val left = Observable.zip(setupSignal,average,{a,b->Pair(a,b)}).filter { it.first<it.second }.map { it.first }
        val right = Observable.zip(setupSignal,average,{a,b->Pair(a,b)}).filter { it.first>it.second }.map { it.first }

        val leftFin = left.list().map { it.takeLast(10) }.doOnNext { onSetupProgressSubject.onNext(null) }.filter { it.count()==10 }.filter {
            val mutable = it.toMutableList()
            mutable.remove(it.max())
            mutable.remove(it.min())
            val diff = mutable.max()!!-mutable.min()!!
            Log.e("aaa","left diff: $diff")
            diff < 50
        }
        val rightFin = right.list().map{it.takeLast(10)}.doOnNext { onSetupProgressSubject.onNext(null) }.filter { it.count()==10 }.filter {
            val mutable = it.toMutableList()
            mutable.remove(it.max())
            mutable.remove(it.min())
            val diff = mutable.max()!!-mutable.min()!!
            Log.e("aaa","right diff: $diff")
            diff < 50
        }
        Observable.combineLatest(leftFin.timestamp(),rightFin.timestamp(),{a,b->Pair(a,b)}).filter {
            Math.abs(it.first.timestampMillis-it.second.timestampMillis)<500
        }.first().subscribe({
            angleFactory.leftBounds = it.first.value.average().toFloat()
            angleFactory.rightBounds = it.second.value.average().toFloat()
            Log.e("aaa", "complete!! left: ${angleFactory.leftBounds} right: ${angleFactory.rightBounds}")
            onSetupCompletedSubject.onNext(null)
        },{
            Log.e("aaa","err:",it)
        })


        val detectAngles = Observable.merge(touchBeganSubject.map { Pair("began", it) }, touchMovedSubject.map { Pair("move", it) }, touchEndedSubject.map { Pair("ended", it) }).skipUntil(onSetupCompletedSubject).map {
            Pair(it.first, angleFactory.toAngle(it.second))
        }.publish()
        val sequence = detectAngles.takeUntil(detectAngles.throttleLast(50, TimeUnit.MILLISECONDS).filter { it.first == "ended" })
        detectAngles.connect().autoUnSubscribe()

        val seq_began = sequence.map { it.second }.pre().filter { it.first==null }.map{it.second}.repeat().publish().apply { connect().autoUnSubscribe() }
        val seq_move = sequence.skip(1).map { it.second }.repeat().publish().apply { connect().autoUnSubscribe() }
        val seq_ended = sequence.last().map { it.second }.repeat().publish().apply { connect().autoUnSubscribe() }

        seq_began.subscribe {
            Log.e("aaa","BEGAN: ${it.value}")
        }
        seq_move.subscribe {
            Log.e("aaa","MOVE: ${it.value}")
        }
        seq_ended.subscribe {
            Log.e("aaa","END: ${it.value}")
        }

        val tap = sequence.first().timestamp().zipWith(sequence.last().timestamp(), { first, last -> Pair(first, last) }).repeat()
                .filter { it.second.timestampMillis - it.first.timestampMillis < tapDetectDuration }//時間制限
                .filter { (it.second.value.second - it.first.value.second).abs < tapDetectTolerance }//移動距離制限
                .map { it.second }

        tap.pre().map {
            if (it.second.timestampMillis - (it.first?.timestampMillis ?: 0L) < 500L) "doubleTap" else "tap"
        }.subscribe {
            if(it == "tap"){
                onTapSubject.onNext(null)
            }else{
                onDoubleTapSubject.onNext(null)
            }
            Log.e("aaa", it)
        }

        val moveDelta = seq_move.pre().map { Pair(it.second, it.second - (it.first ?: it.second)) }//move,moveDelta
        val scrollStart = moveDelta.map { Math.abs(it.second.value180) }.filter { deltaScale -> 0.3 < deltaScale && deltaScale < 30 }
        val scrollStroke = scrollStart.takeUntil(seq_ended)
        scrollStroke.take(1).doOnNext {
            Log.e("aaa","scrollBegan")
            onScrollBeganSubject.onNext(null)
        }.zipWith(scrollStroke.count(), { a, b -> Pair(a, b) }).repeat().subscribe()
        scrollStroke.count().repeat().subscribe {
            if (it != 0) {
                Log.e("aaa","scrollEnded")
                onScrollEndedSubject.onNext(null)
            }
        }
        scrollStroke.repeat().subscribe {
            onScrollSubject.onNext(it)
            Log.e("aaa","scroll")
        }
//
//        val ts = seq_began.timestamp().first().concatWith(seq_move.timestamp()).list()
//        val touchStroke = Observable.combineLatest(ts, seq_ended.timestamp(), { list, end ->
//            val a = list.toMutableList()
//            a.add(end)
//            a
//        }).first()
//        val touchStroke = detectTouchBegan.first().concatWith(detectTouchMoved).takeUntil(detectTouchEnded)
        sequence.timestamp().toList().repeat().filter { it.count()>=2 }.subscribe {
            Log.e("aaa", "strList count:${it.count()} f:${it.first().value} l:${it.last().value}")
            val startAngle = it.first().value.second
            val endAngle = it.last().value.second
            val angleDelta = (startAngle - endAngle).abs
            val timeDelta = it.last().timestampMillis - it.first().timestampMillis
//            Log.e("aaa", "tD:$timeDelta s:${startAngle.value} e:${endAngle.value}")

            val positionCondition = angleDelta > tapDetectTolerance
            val timeCondition = timeDelta < tapDetectDuration
            if (positionCondition && timeCondition) {//swipe
                val swipe = Swipe(startAngle, endAngle, timeDelta)
                onSwipeSubject.onNext(swipe)
                Log.e("aaa", "swipe speed:${swipe.speed} direction: ${swipe.direction}")
            }
        }


    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscriptions.unsubscribe()
    }

    private fun Subscription.autoUnSubscribe() {
        subscriptions.add(this)
    }
}
