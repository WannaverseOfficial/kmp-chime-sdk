package com.wannaverse.chimesdk

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.objc.class_addProtocol
import platform.objc.objc_getProtocol
import platform.objc.object_getClass

internal data class ProtocolDescriptor(val candidates: List<String>) {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    internal fun forceRegisterProtocol(`class`: Any) = runCatching {
        val cls = object_getClass(`class`)
        val protocol = candidates.firstNotNullOf { objc_getProtocol(it) }
        class_addProtocol(cls, protocol)
    }
}


