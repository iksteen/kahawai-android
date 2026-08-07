package com.kolktech.kahawai.testutil

import android.app.Application
import io.mockk.every
import io.mockk.mockk

/// Relaxed [Application] mock for `AndroidViewModel` tests. `getString`
/// can't resolve real Android resources on the plain JVM test classpath,
/// so both overloads are stubbed to return a resId+args sentinel
/// ("res:<id>" or "res:<id>:<arg1>,<arg2>") — tests assert against this
/// sentinel (comparing against the relevant `R.string.*` id) instead of
/// duplicating strings.xml wording, which would rot every time copy changes.
fun relaxedApplication(): Application {
    val app = mockk<Application>(relaxed = true)
    every { app.getString(any()) } answers { "res:${firstArg<Int>()}" }
    every { app.getString(any(), *anyVararg<Any>()) } answers {
        // getString(Int, vararg Any) is (int, Object[]) at the JVM ABI —
        // invocation.args is [resId, formatArgsArray], not a flattened list.
        val args = it.invocation.args
        val resId = args[0]
        val formatArgs = (args.getOrNull(1) as? Array<*>)?.toList().orEmpty()
        if (formatArgs.isEmpty()) "res:$resId" else "res:$resId:${formatArgs.joinToString(",")}"
    }
    return app
}
