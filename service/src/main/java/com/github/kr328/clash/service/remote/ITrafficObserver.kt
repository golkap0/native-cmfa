package com.github.kr328.clash.service.remote

import com.github.kr328.kaidl.BinderInterface

@BinderInterface
fun interface ITrafficObserver {
    fun updateTrafficTotal(total: Long)
}
