package com.example.engine

import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Optional runtime bridge to the gomobile-generated NanoMarshal Go core.
 *
 * Reflection intentionally keeps the normal Android build working when the
 * generated AAR has not been built yet. In that case VoxelPathfinder uses its
 * existing Kotlin implementation as a fallback.
 */
internal object GoPathfinderBridge {
    private const val TAG = "NanoMarshalGoCore"
    private val intType = Int::class.javaPrimitiveType
    @Volatile private var runtimeFailed = false

    private val pathMethod: Method? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val classNames = arrayOf(
            "com.example.nanomarshal.nanocore.Nanocore",
            "com.example.nanomarshal.nanocore.Core"
        )
        for (className in classNames) {
            try {
                val bindingClass = Class.forName(className)
                val method = bindingClass.methods.firstOrNull {
                    (it.name == "findPath" || it.name == "FindPath") &&
                        Modifier.isStatic(it.modifiers) &&
                        it.parameterTypes.size == 7 &&
                        it.parameterTypes.take(6).all { type -> type == intType } &&
                        it.parameterTypes[6] == FloatArray::class.java &&
                        it.returnType == IntArray::class.java
                }
                if (method != null) {
                    Log.i(TAG, "Go A* binding loaded from $className")
                    return@lazy method
                }
            } catch (_: ClassNotFoundException) {
                // Try the next generated-name candidate.
            } catch (error: LinkageError) {
                Log.w(TAG, "Go A* binding could not be linked; Kotlin fallback remains active", error)
            }
        }
        Log.i(TAG, "Go A* AAR not found; Kotlin pathfinder fallback remains active")
        null
    }

    val isAvailable: Boolean
        get() = !runtimeFailed && pathMethod != null

    @Synchronized
    fun findPath(
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        targetX: Int,
        targetY: Int,
        costs: FloatArray
    ): IntArray? {
        if (runtimeFailed) return null
        val method = pathMethod ?: return null
        return try {
            val result = method.invoke(
                null,
                width,
                height,
                startX,
                startY,
                targetX,
                targetY,
                costs
            ) as? IntArray
            if (result == null || result.isEmpty() || result.size % 2 != 0) {
                Log.w(TAG, "Go A* returned an invalid path; disabling bridge for this session")
                runtimeFailed = true
                null
            } else {
                result
            }
        } catch (error: ReflectiveOperationException) {
            Log.w(TAG, "Go A* call failed; Kotlin fallback remains active", error)
            runtimeFailed = true
            null
        } catch (error: LinkageError) {
            Log.w(TAG, "Go A* native library could not be linked; Kotlin fallback remains active", error)
            runtimeFailed = true
            null
        }
    }
}
