package com.example.engine

/**
 * Custom GLSL Shader definitions for applying a 'Nanopunk' aesthetic to voxel geometry.
 * Features procedural nanite circuit grid generation, scanlines, bio-circuit pulses,
 * chromatic rim lighting, and holographic lattice shaders via OpenGL ES 2.0 / 3.0.
 */
object NanopunkGlslShader {

    const val NANOPUNK_VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec4 aColor;
        attribute vec2 aTexCoord;

        uniform float uTime;
        uniform float uGlitchIntensity;

        varying vec4 vColor;
        varying vec2 vPosition;
        varying vec2 vTexCoord;
        varying float vTime;

        void main() {
            vColor = aColor;
            vPosition = aPosition.xy;
            vTexCoord = aTexCoord;
            vTime = uTime;

            vec4 pos = aPosition;

            // Nanopunk subtle vertex glitch displacement
            if (uGlitchIntensity > 0.0) {
                float glitch = sin(aPosition.y * 50.0 + uTime * 30.0);
                if (glitch > 0.85) {
                    pos.x += sin(uTime * 40.0) * 0.02 * uGlitchIntensity;
                }
            }

            gl_Position = pos;
        }
    """

    const val NANOPUNK_FRAGMENT_SHADER = """
        precision mediump float;

        varying vec4 vColor;
        varying vec2 vPosition;
        varying vec2 vTexCoord;
        varying float vTime;

        uniform vec2 uResolution;
        uniform float uTime;
        uniform float uNanopunkGlow;
        uniform float uPulseSpeed;
        uniform int uNanopunkPreset; // 0: Nanite Cyber Circuit, 1: Holographic Wireframe, 2: Bio-Hazard Pulse, 3: Quantum Shield

        // Pseudo-random noise generator
        float rand(vec2 co) {
            return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
        }

        // 2D Noise for nanite lattice turbulence
        float noise(vec2 st) {
            vec2 i = floor(st);
            vec2 f = fract(st);
            float a = rand(i);
            float b = rand(i + vec2(1.0, 0.0));
            float c = rand(i + vec2(0.0, 1.0));
            float d = rand(i + vec2(1.0, 1.0));
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
        }

        void main() {
            vec4 baseColor = vColor;
            vec2 st = gl_FragCoord.xy / (uResolution + vec2(0.001));

            // 1. Nanopunk Scanline & Grid Effect
            float scanline = sin(st.y * 350.0 + uTime * 10.0) * 0.08;
            float gridPattern = step(0.95, fract(st.x * 50.0)) + step(0.95, fract(st.y * 50.0));

            // 2. Nanite Energy Circuit Waves (traveling glow lines)
            float waveX = sin(vPosition.x * 0.04 + uTime * uPulseSpeed * 3.5);
            float waveY = cos(vPosition.y * 0.04 - uTime * uPulseSpeed * 2.8);
            float circuitPulse = pow(abs(waveX * waveY), 3.5) * 0.65;

            // 3. Cybernetic Neon Color Palettes
            vec3 neonCyan = vec3(0.0, 0.94, 1.0);     // #00F0FF Nanite Cyan
            vec3 laserMagenta = vec3(1.0, 0.0, 0.55); // #FF008C Laser Magenta
            vec3 bioGreen = vec3(0.22, 1.0, 0.08);     // #39FF14 Bio Green
            vec3 hazardGold = vec3(1.0, 0.84, 0.0);    // #FFD700 Hazard Gold

            vec3 nanopunkTone = baseColor.rgb;

            if (uNanopunkPreset == 0) {
                // Preset 0: Nanite Cyber Circuit
                nanopunkTone = mix(nanopunkTone, neonCyan, circuitPulse * 0.75);
                nanopunkTone += laserMagenta * gridPattern * 0.35;
            } else if (uNanopunkPreset == 1) {
                // Preset 1: Holographic Wireframe Grid
                float holoGlitch = step(0.97, rand(vec2(uTime * 0.1, st.y)));
                nanopunkTone = mix(neonCyan * 1.2, laserMagenta, sin(uTime * 3.0) * 0.5 + 0.5);
                nanopunkTone += vec3(gridPattern * 0.6) + vec3(holoGlitch * 0.4);
            } else if (uNanopunkPreset == 2) {
                // Preset 2: Bio-Hazard Plasma Surge
                float bioPulse = sin(uTime * 5.0 + vPosition.x * 0.02) * 0.5 + 0.5;
                nanopunkTone = mix(nanopunkTone, bioGreen, bioPulse * 0.8);
                nanopunkTone += hazardGold * circuitPulse * 0.5;
            } else {
                // Preset 3: Quantum Shield Lattice
                float shieldGrid = abs(sin(vPosition.x * 0.1 + uTime)) * abs(cos(vPosition.y * 0.1 + uTime));
                nanopunkTone = mix(nanopunkTone, neonCyan * 1.4, shieldGrid * 0.55);
            }

            // Apply scanlines, emissive glow scalar, and procedural nanite shimmer
            nanopunkTone += vec3(scanline);
            nanopunkTone *= uNanopunkGlow;

            float naniteShimmer = noise(vPosition.xy * 0.08 + vec2(uTime * 2.0)) * 0.12;
            nanopunkTone += vec3(naniteShimmer);

            gl_FragColor = vec4(nanopunkTone, baseColor.a);
        }
    """
}
