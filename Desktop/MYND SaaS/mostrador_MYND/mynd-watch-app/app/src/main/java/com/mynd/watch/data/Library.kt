package com.mynd.watch.data

import android.content.Context
import com.mynd.watch.studio.DataContract
import com.mynd.watch.studio.FaceLibrary
import java.io.File

object Library {
    lateinit var lib: FaceLibrary
        private set

    fun init(ctx: Context) {
        if (::lib.isInitialized) return
        lib = FaceLibrary(File(ctx.filesDir, "library"))
        if (lib.list().isEmpty()) {
            val seeds = listOf(
                Triple("MYND HUD",       "faces/MYND_HUD.bin",       DataContract.REAL_WEATHER),
                Triple("MYND HUD Bambu", "faces/MYND_HUD_BAMBU.bin", DataContract.BAMBU_V4)
            )
            for ((name, asset, contract) in seeds) {
                val ref = lib.save(name, ctx.assets.open(asset).readBytes(), "original validado", contract)
                lib.markApproved(ref)
            }
        }
    }
}
