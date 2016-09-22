package com.milboxtouch.white.milboxtouch_sdk

/**
 * Created by moajo on 2016/09/14.
 */
class ListenerSet<in T: (arg:Any?)->Void> {

    private val listeners:MutableSet<T> = mutableSetOf()

    fun addListener(listener:T){
        listeners.add(listener)
    }

    fun removeListener(listener:T){
        listeners.remove(listener)
    }
    fun clear(){
        listeners.clear()
    }

    fun invoke(args:Any?){
        listeners.forEach { it(args) }
    }
}