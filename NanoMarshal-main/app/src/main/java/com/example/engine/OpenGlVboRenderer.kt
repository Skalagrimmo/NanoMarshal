package com.example.engine

import android.opengl.GLES20
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Standard OpenGL ES Vertex Attribute Layout structure:
 * Position: X, Y, Z (3 Floats = 12 Bytes)
 * Color: R, G, B, A (4 Unsigned Bytes = 4 Bytes) - uses GL_UNSIGNED_BYTE
 * Normal/UV: NX, NY (2 Floats = 8 Bytes)
 * Total Vertex Stride: 24 Bytes
 */
data class OpenGlVertex(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val redByte: Byte,   // GL_UNSIGNED_BYTE (0..255 mapped to byte)
    val greenByte: Byte, // GL_UNSIGNED_BYTE
    val blueByte: Byte,  // GL_UNSIGNED_BYTE
    val alphaByte: Byte, // GL_UNSIGNED_BYTE
    val u: Float = 0f,
    val v: Float = 0f
)

/**
 * Hardware OpenGL ES 2.0 / 3.0 Rendering Engine with Vertex Buffer Objects (VBO),
 * Dynamic Buffer Allocation via [GLES20.GL_DYNAMIC_DRAW], array-based primitive rendering via [GLES20.glDrawArrays],
 * and compact color packing using [GLES20.GL_UNSIGNED_BYTE].
 */
class OpenGlVboRenderer {

    // VBO Handles & Buffer Identifiers
    private var positionVboId: Int = 0
    private var colorVboId: Int = 0
    private var lineVboId: Int = 0

    private var programId: Int = 0
    private var isGlInitialized = false

    // Direct Native Memory Buffers
    private var nativeFloatBuffer: FloatBuffer? = null
    private var nativeByteBuffer: ByteBuffer? = null

    // Dynamic Geometry VBO Cache
    private val vertexStream = mutableListOf<OpenGlVertex>()
    private val lineVertexStream = mutableListOf<OpenGlVertex>()
    private val cachedPath = Path()

    companion object {
        const val BYTES_PER_FLOAT = 4
        const val BYTES_PER_VERTEX = 24 // 3 floats (pos) + 4 ubytes (color) + 2 floats (uv)

        // Embedded GLES Shaders from NanopunkGlslShader
        private const val VERTEX_SHADER_CODE = NanopunkGlslShader.NANOPUNK_VERTEX_SHADER
        private const val FRAGMENT_SHADER_CODE = NanopunkGlslShader.NANOPUNK_FRAGMENT_SHADER
    }

    /**
     * Initializes GLES20 shaders, compiles Nanopunk GLSL program, and generates VBO handles via [GLES20.glGenBuffers].
     */
    fun initGlPipelines() {
        if (isGlInitialized) return

        try {
            // Compile Vertex Shader
            val vertShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
            GLES20.glShaderSource(vertShader, VERTEX_SHADER_CODE)
            GLES20.glCompileShader(vertShader)

            // Compile Fragment Shader
            val fragShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
            GLES20.glShaderSource(fragShader, FRAGMENT_SHADER_CODE)
            GLES20.glCompileShader(fragShader)

            // Link Shader Program
            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vertShader)
            GLES20.glAttachShader(programId, fragShader)
            GLES20.glLinkProgram(programId)

            // Generate Vertex Buffer Object (VBO) handles
            val vboIds = IntArray(3)
            GLES20.glGenBuffers(3, vboIds, 0)
            positionVboId = vboIds[0]
            colorVboId = vboIds[1]
            lineVboId = vboIds[2]

            isGlInitialized = true
        } catch (e: Exception) {
            // Fallback for non-OpenGL context canvas fallback
            isGlInitialized = false
        }
    }

    /**
     * Clears current VBO vertex stream for the upcoming frame pass.
     */
    fun beginFrame() {
        vertexStream.clear()
        lineVertexStream.clear()
    }

    /**
     * Pushes a colored triangle primitive into the VBO vertex stream.
     * Colors are packed into compact GL_UNSIGNED_BYTE values (0..255).
     */
    fun pushTriangle(
        x1: Float, y1: Float, z1: Float = 0f,
        x2: Float, y2: Float, z2: Float = 0f,
        x3: Float, y3: Float, z3: Float = 0f,
        color: Color
    ) {
        val r = (color.red * 255f).toInt().coerceIn(0, 255).toByte()
        val g = (color.green * 255f).toInt().coerceIn(0, 255).toByte()
        val b = (color.blue * 255f).toInt().coerceIn(0, 255).toByte()
        val a = (color.alpha * 255f).toInt().coerceIn(0, 255).toByte()

        vertexStream.add(OpenGlVertex(x1, y1, z1, r, g, b, a))
        vertexStream.add(OpenGlVertex(x2, y2, z2, r, g, b, a))
        vertexStream.add(OpenGlVertex(x3, y3, z3, r, g, b, a))
    }

    /**
     * Pushes a line segment primitive into the VBO line vertex stream.
     */
    fun pushLine(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        color: Color
    ) {
        val r = (color.red * 255f).toInt().coerceIn(0, 255).toByte()
        val g = (color.green * 255f).toInt().coerceIn(0, 255).toByte()
        val b = (color.blue * 255f).toInt().coerceIn(0, 255).toByte()
        val a = (color.alpha * 255f).toInt().coerceIn(0, 255).toByte()

        lineVertexStream.add(OpenGlVertex(startX, startY, 0f, r, g, b, a))
        lineVertexStream.add(OpenGlVertex(endX, endY, 0f, r, g, b, a))
    }

    /**
     * Pushes Voronoi Diagram cells into the OpenGL VBO buffer streams.
     */
    fun pushVoronoiDiagram(voronoi: VoronoiDiagram) {
        for (cell in voronoi.cells) {
            val site = cell.site
            val verts = cell.vertices
            if (verts.size < 3) continue

            // Fan triangulation around Voronoi site seed
            for (i in 0 until verts.size) {
                val nextIdx = (i + 1) % verts.size
                pushTriangle(
                    x1 = site.x, y1 = site.y, z1 = 0f,
                    x2 = verts[i].first, y2 = verts[i].second, z2 = 0f,
                    x3 = verts[nextIdx].first, y3 = verts[nextIdx].second, z3 = 0f,
                    color = site.color.copy(alpha = 0.12f)
                )
            }
        }

        // Push Voronoi bisector edges
        for (edge in voronoi.edges) {
            pushLine(
                startX = edge.startX, startY = edge.startY,
                endX = edge.endX, endY = edge.endY,
                color = Color.Cyan.copy(alpha = 0.45f)
            )
        }
    }

    /**
     * Pushes a voxel geometry block (quad face & wireframe bezel) into the VBO vertex stream.
     */
    fun pushVoxelBlock(
        centerX: Float, centerY: Float,
        width: Float, height: Float,
        baseColor: Color,
        wireframeColor: Color = Color(0xFF00F0FF)
    ) {
        val halfW = width / 2f
        val halfH = height / 2f

        val x1 = centerX - halfW
        val y1 = centerY - halfH
        val x2 = centerX + halfW
        val y2 = centerY + halfH

        // Triangle 1
        pushTriangle(x1, y1, 0f, x2, y1, 0f, x2, y2, 0f, baseColor)
        // Triangle 2
        pushTriangle(x1, y1, 0f, x2, y2, 0f, x1, y2, 0f, baseColor)

        // Nanopunk Neon Bezel Wireframe Edges
        pushLine(x1, y1, x2, y1, wireframeColor)
        pushLine(x2, y1, x2, y2, wireframeColor)
        pushLine(x2, y2, x1, y2, wireframeColor)
        pushLine(x1, y2, x1, y1, wireframeColor)
    }

    /**
     * Uploads dynamic vertex stream to OpenGL VBO using [GLES20.glBufferData] with [GLES20.GL_DYNAMIC_DRAW],
     * sets Nanopunk GLSL shader uniforms ([uTime], [uResolution], [uNanopunkGlow], [uPulseSpeed], [uNanopunkPreset]),
     * specifying [GLES20.GL_UNSIGNED_BYTE] for color attribute layout, and renders via [GLES20.glDrawArrays].
     */
    fun renderOpenGlVboPass(
        timeSec: Float = (System.currentTimeMillis() % 100000L) / 1000f,
        resolutionX: Float = 1080f,
        resolutionY: Float = 1920f,
        nanopunkGlow: Float = 1.35f,
        pulseSpeed: Float = 1.2f,
        nanopunkPreset: Int = 0,
        glitchIntensity: Float = 0.0f
    ) {
        if (!isGlInitialized || vertexStream.isEmpty()) return

        // 1. Prepare Direct Native Position FloatBuffer
        val requiredFloatBytes = vertexStream.size * 3 * BYTES_PER_FLOAT
        var floatBuf = nativeFloatBuffer
        if (floatBuf == null || floatBuf.capacity() < vertexStream.size * 3) {
            val byteBuf = ByteBuffer.allocateDirect(requiredFloatBytes * 2).order(ByteOrder.nativeOrder())
            floatBuf = byteBuf.asFloatBuffer()
            nativeFloatBuffer = floatBuf
        }
        floatBuf.clear()
        for (v in vertexStream) {
            floatBuf.put(v.x)
            floatBuf.put(v.y)
            floatBuf.put(v.z)
        }
        floatBuf.position(0)

        // 2. Prepare Direct Native Color ByteBuffer using GL_UNSIGNED_BYTE
        val requiredColorBytes = vertexStream.size * 4
        var byteBuf = nativeByteBuffer
        if (byteBuf == null || byteBuf.capacity() < requiredColorBytes) {
            byteBuf = ByteBuffer.allocateDirect(requiredColorBytes * 2).order(ByteOrder.nativeOrder())
            nativeByteBuffer = byteBuf
        }
        byteBuf.clear()
        for (v in vertexStream) {
            byteBuf.put(v.redByte)
            byteBuf.put(v.greenByte)
            byteBuf.put(v.blueByte)
            byteBuf.put(v.alphaByte)
        }
        byteBuf.position(0)

        // 3. Upload Position VBO data using GLES20.glBufferData with GL_DYNAMIC_DRAW
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionVboId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            vertexStream.size * 3 * BYTES_PER_FLOAT,
            floatBuf,
            GLES20.GL_DYNAMIC_DRAW
        )

        // 4. Upload Color VBO data using GLES20.glBufferData with GL_DYNAMIC_DRAW
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorVboId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            vertexStream.size * 4,
            byteBuf,
            GLES20.GL_DYNAMIC_DRAW
        )

        // 5. Execute GLES20.glDrawArrays for GL_TRIANGLES primitive rendering
        GLES20.glUseProgram(programId)

        // Set Nanopunk GLSL Shader Uniforms
        val uTimeHandle = GLES20.glGetUniformLocation(programId, "uTime")
        if (uTimeHandle >= 0) GLES20.glUniform1f(uTimeHandle, timeSec)

        val uResHandle = GLES20.glGetUniformLocation(programId, "uResolution")
        if (uResHandle >= 0) GLES20.glUniform2f(uResHandle, resolutionX, resolutionY)

        val uGlowHandle = GLES20.glGetUniformLocation(programId, "uNanopunkGlow")
        if (uGlowHandle >= 0) GLES20.glUniform1f(uGlowHandle, nanopunkGlow)

        val uPulseHandle = GLES20.glGetUniformLocation(programId, "uPulseSpeed")
        if (uPulseHandle >= 0) GLES20.glUniform1f(uPulseHandle, pulseSpeed)

        val uPresetHandle = GLES20.glGetUniformLocation(programId, "uNanopunkPreset")
        if (uPresetHandle >= 0) GLES20.glUniform1i(uPresetHandle, nanopunkPreset)

        val uGlitchHandle = GLES20.glGetUniformLocation(programId, "uGlitchIntensity")
        if (uGlitchHandle >= 0) GLES20.glUniform1f(uGlitchHandle, glitchIntensity)

        val posHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        val colorHandle = GLES20.glGetAttribLocation(programId, "aColor")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionVboId)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, 0)

        // Attribute binding for color using GLES20.GL_UNSIGNED_BYTE normalized
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorVboId)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_UNSIGNED_BYTE, true, 0, 0)

        // Execute OpenGL Draw Call
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexStream.size)

        // Unbind VBOs
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Bridges OpenGL VBO vertex buffer stream directly into Compose Canvas [DrawScope]
     * applying software-emulated Nanopunk GLSL circuit pulse & scanline color shifts
     * so dynamic VBO buffers render seamlessly on all Android device canvases.
     */
    fun drawVboBridgeToCanvas(
        drawScope: DrawScope,
        timeSec: Float = (System.currentTimeMillis() % 100000L) / 1000f,
        nanopunkPreset: Int = 0,
        glowIntensity: Float = 1.25f
    ) {
        // Render VBO Triangles
        var i = 0
        while (i + 2 < vertexStream.size) {
            val v1 = vertexStream[i]
            val v2 = vertexStream[i + 1]
            val v3 = vertexStream[i + 2]

            cachedPath.reset()
            cachedPath.moveTo(v1.x, v1.y)
            cachedPath.lineTo(v2.x, v2.y)
            cachedPath.lineTo(v3.x, v3.y)
            cachedPath.close()

            // Extract Base Color from GL_UNSIGNED_BYTE
            val r = (v1.redByte.toInt() and 0xFF) / 255f
            val g = (v1.greenByte.toInt() and 0xFF) / 255f
            val b = (v1.blueByte.toInt() and 0xFF) / 255f
            val a = (v1.alphaByte.toInt() and 0xFF) / 255f

            // Nanopunk circuit pulse modulation
            val wave = (kotlin.math.sin(timeSec * 4f + (v1.x + v1.y) * 0.01f) * 0.5f + 0.5f)
            val nanopunkColor = when (nanopunkPreset) {
                0 -> Color( // Nanite Cyber Circuit (Cyan + Magenta)
                    red = (r * 0.7f + 0.1f * wave).coerceIn(0f, 1f),
                    green = (g * 0.8f + 0.94f * wave * glowIntensity * 0.35f).coerceIn(0f, 1f),
                    blue = (b * 0.9f + 1.0f * wave * glowIntensity * 0.45f).coerceIn(0f, 1f),
                    alpha = a
                )
                1 -> Color( // Holographic Wireframe
                    red = (r * 0.5f + 1.0f * wave * glowIntensity * 0.5f).coerceIn(0f, 1f),
                    green = (g * 0.3f).coerceIn(0f, 1f),
                    blue = (b * 0.8f + 0.88f * wave * glowIntensity * 0.5f).coerceIn(0f, 1f),
                    alpha = a
                )
                2 -> Color( // Bio-Hazard Pulse
                    red = (r * 0.3f + 0.22f * wave).coerceIn(0f, 1f),
                    green = (g * 0.9f + 1.0f * wave * glowIntensity * 0.55f).coerceIn(0f, 1f),
                    blue = (b * 0.2f).coerceIn(0f, 1f),
                    alpha = a
                )
                else -> Color( // Quantum Shield
                    red = (r * (1f + wave * 0.3f)).coerceIn(0f, 1f),
                    green = (g * (1f + wave * 0.3f)).coerceIn(0f, 1f),
                    blue = (b * (1f + wave * 0.3f)).coerceIn(0f, 1f),
                    alpha = a
                )
            }

            drawScope.drawPath(path = cachedPath, color = nanopunkColor)
            i += 3
        }

        // Render VBO Lines with neon wireframe glow
        var j = 0
        while (j + 1 < lineVertexStream.size) {
            val l1 = lineVertexStream[j]
            val l2 = lineVertexStream[j + 1]

            val color = Color(
                red = (l1.redByte.toInt() and 0xFF) / 255f,
                green = (l1.greenByte.toInt() and 0xFF) / 255f,
                blue = (l1.blueByte.toInt() and 0xFF) / 255f,
                alpha = (l1.alphaByte.toInt() and 0xFF) / 255f
            )

            drawScope.drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(l1.x, l1.y),
                end = androidx.compose.ui.geometry.Offset(l2.x, l2.y),
                strokeWidth = 2.0f
            )
            j += 2
        }
    }
}
