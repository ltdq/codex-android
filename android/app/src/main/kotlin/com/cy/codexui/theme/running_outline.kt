package com.cy.codexui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.shader.RuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/**
 * A "still running" comet that travels around the outline of a chip.
 *
 * This is the one thing in the shell that a gradient cannot draw. Everything else the app animates —
 * panel sizes, tints, chevrons — is a one-dimensional value along a fixed axis, so a brush or an
 * `animate*AsState` covers it. A highlight that runs *around a closed outline* is a per-pixel
 * function of the angle around the shape's centre, which is why it is written as an AGSL shader and
 * driven through `miuix-shader`: the phase is the only thing that changes per frame, and it is a
 * uniform, so a frame costs one JNI call instead of a recomposition.
 *
 * The dot badge it sits next to says *what* the session is doing; the comet says *that it is still
 * doing it*. A static dot cannot: every state in this app — idle, running, waiting for an approval —
 * is a still image otherwise, and the user is left reading the label to tell them apart.
 *
 * @param active whether the outline should be drawn at all. When false nothing is composed, so an
 *   idle chip pays neither the frame clock nor the shader.
 * @param cornerRadius corner radius of the chip, in dp. The shader evaluates a rounded-rect
 *   distance field, so this has to be the same radius the shape was drawn with.
 * @param color the comet's colour.
 * @param thickness how far the band spreads either side of the outline.
 * @param periodMillis one full lap.
 */
@Composable
fun Modifier.runningOutline(
    active: Boolean,
    cornerRadius: Dp,
    color: Color,
    thickness: Dp = 2.dp,
    periodMillis: Int = 2400,
): Modifier {
    // AGSL needs API 33; below that (and on any backend without runtime shaders) the chrome simply
    // has no comet rather than a broken one.
    if (!active || !isRuntimeShaderSupported()) return this

    val transition = rememberInfiniteTransition(label = "runningOutline")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing)),
        label = "runningOutlinePhase",
    )
    // A shader that fails to compile would take the whole chip down with it, and the effect is
    // decoration: a null shader draws nothing.
    val shader = remember { runCatching { RuntimeShader(RUNNING_OUTLINE_AGSL) }.getOrNull() }
    val brush = remember(shader) { shader?.asBrush() }
    if (shader == null || brush == null) return this

    return this.drawBehind {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("corner", cornerRadius.toPx())
        shader.setFloatUniform("thickness", thickness.toPx())
        // Four floats rather than `setColorUniform`: the colour-specific entry point validates the
        // uniform against the shader's declared type and rejects a plain `vec4` on this platform
        // ("attempting to set a color uniform using the non-color specific APIs"). A vec4 is a vec4.
        shader.setFloatUniform("color", color.red, color.green, color.blue, color.alpha)
        shader.setFloatUniform("phase", phase)
        drawRect(brush = brush, size = size)
    }
}

/**
 * A band that hugs a rounded rectangle's outline, brightened along a head that walks the perimeter.
 *
 * `sdRoundRect` is the standard signed distance to a rounded rect; `abs(d)` is therefore the distance
 * to the *outline* wherever you are, which is what makes the band follow the silhouette instead of
 * sitting in a circle inside it. The angle around the centre stands in for arc length — for a shape
 * this close to square the two are nearly proportional, and it keeps the shader free of an arc-length
 * table.
 */
private const val RUNNING_OUTLINE_AGSL = """
uniform float2 size;
uniform float corner;
uniform float thickness;
uniform float phase;
uniform vec4 color;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundRect(p, halfSize, corner);

    // 1 on the outline, falling to 0 within `thickness`.
    float band = 1.0 - smoothstep(0.0, max(thickness, 0.5), abs(d));

    // Position along the outline as a 0..1 angle, and how far this pixel trails the head.
    float angle = atan(p.y, p.x) / 6.28318530718 + 0.5;
    float behind = fract(fract(phase) - angle);

    // A short, bright head with a long tail: pow() is the whole falloff.
    float comet = pow(1.0 - behind, 4.0);

    float a = band * comet * color.a;
    return half4(half3(color.rgb * a), half(a));
}
"""
