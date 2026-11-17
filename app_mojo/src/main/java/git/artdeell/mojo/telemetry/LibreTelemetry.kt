package git.artdeell.mojo.telemetry

import android.util.Log

data class TelemetrySnapshot(
    val fps: Float,
    val onePercentLow: Float,
    val frameTimeMs: Float,
    val jitterMs: Float,
    val ramUsedMb: Int,
    val thermalHeadroom: Float,
    val isHibernate: Boolean,
    val frameCount: Long
)

object LibreTelemetry {
    private const val TAG = "LibreTelemetry"

    init {
        try {
            System.loadLibrary("mojort")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load mojort library", t)
        }
    }

    private val packedBuffer = LongArray(8)

    @JvmStatic
    external fun nativeRecordFrame(deltaMs: Float)

    @JvmStatic
    external fun nativeOnFramePresent(): Long

    @JvmStatic
    external fun nativeComputeTelemetryPacked(outArray: LongArray): Int

    @JvmStatic
    external fun nativeSetHibernate(enabled: Boolean, targetFps: Int)

    @JvmStatic
    external fun nativeIsHibernate(): Boolean

    @JvmStatic
    external fun nativeNotifyInput()

    @JvmStatic
    external fun nativeThrottleSleep()

    @JvmStatic
    fun nativeSetAfk(active: Boolean, targetFps: Int) = nativeSetHibernate(active, targetFps)

    @JvmStatic
    fun nativeIsAfk(): Boolean = nativeIsHibernate()

    @Synchronized
    fun getSnapshot(): TelemetrySnapshot {
        try {
            val count = nativeComputeTelemetryPacked(packedBuffer)
            if (count >= 8) {
                return TelemetrySnapshot(
                    fps = packedBuffer[0] / 100f,
                    onePercentLow = packedBuffer[1] / 100f,
                    frameTimeMs = packedBuffer[2] / 100f,
                    jitterMs = packedBuffer[3] / 100f,
                    ramUsedMb = packedBuffer[4].toInt(),
                    thermalHeadroom = packedBuffer[5] / 100f,
                    isHibernate = packedBuffer[6] == 1L,
                    frameCount = packedBuffer[7]
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to compute telemetry", t)
        }
        return TelemetrySnapshot(0f, 0f, 0f, 0f, 0, 0f, false, 0L)
    }
}
