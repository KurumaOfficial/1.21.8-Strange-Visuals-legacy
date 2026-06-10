#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D BlurSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform GlassData {
    vec4 screenParams;
    vec4 tintColor;
    vec4 effectParams;
    vec4 glowColor;
};

in vec2 texCoord;

out vec4 fragColor;

vec3 adjustSaturation(vec3 color, float sat) {
    vec3 luminance = vec3(0.2126, 0.7152, 0.0722);
    float grey = dot(color, luminance);
    return mix(vec3(grey), color, sat);
}

vec2 safeUv(sampler2D sampler, vec2 uv) {
    vec2 texSize = vec2(textureSize(sampler, 0));
    vec2 texel = 0.5 / max(texSize, vec2(1.0));
    return clamp(uv, texel, vec2(1.0) - texel);
}

void main() {
    vec2 sceneUv = safeUv(SceneSampler, texCoord);
    vec2 blurUv = safeUv(BlurSampler, texCoord);
    vec2 maskUv = safeUv(MaskSampler, texCoord);

    vec4 scene = texture(SceneSampler, sceneUv);
    vec4 blurred = texture(BlurSampler, blurUv);
    float mask = texture(MaskSampler, maskUv).r;

    if (mask < 0.01) {
        fragColor = scene;
        return;
    }

    vec3 glassColor = adjustSaturation(blurred.rgb, screenParams.z);

    float tintIntens = effectParams.x;
    if (tintColor.a > 0.0) {
        glassColor = mix(glassColor, tintColor.rgb, tintIntens * tintColor.a);
    }

    if (screenParams.w > 0.5) {
        glassColor = glassColor * 1.05 + vec3(0.02);
    }

    float edgeGlow = effectParams.y;
    float glowStrength = effectParams.z;
    if (edgeGlow > 0.0) {
        vec2 texelSize = 1.0 / vec2(screenParams.x, screenParams.y);
        float ml = texture(MaskSampler, safeUv(MaskSampler, maskUv + vec2(-texelSize.x, 0.0))).r;
        float mr = texture(MaskSampler, safeUv(MaskSampler, maskUv + vec2(texelSize.x, 0.0))).r;
        float mt = texture(MaskSampler, safeUv(MaskSampler, maskUv + vec2(0.0, texelSize.y))).r;
        float mb = texture(MaskSampler, safeUv(MaskSampler, maskUv + vec2(0.0, -texelSize.y))).r;
        float edge = abs(mr - ml) + abs(mt - mb);
        edge = clamp(edge * 2.0, 0.0, 1.0);
        vec3 edgeTint = mix(vec3(1.0), glowColor.rgb, clamp(glowStrength * glowColor.a, 0.0, 1.0));
        glassColor += edgeTint * edge * edgeGlow * (1.0 + glowStrength);
    }

    // Liquid-glass refraction: sample blur with a soft mask-driven offset.
    vec2 texelSize = 1.0 / vec2(screenParams.x, screenParams.y);
    float ml = texture(MaskSampler, safeUv(MaskSampler, maskUv + vec2(-texelSize.x * 2.0, 0.0))).r;
    float mr = texture(MaskSampler, safeUv(MaskSampler, maskUv + vec2(texelSize.x * 2.0, 0.0))).r;
    float mt = texture(MaskSampler, safeUv(MaskSampler, maskUv + vec2(0.0, texelSize.y * 2.0))).r;
    float mb = texture(MaskSampler, safeUv(MaskSampler, maskUv + vec2(0.0, -texelSize.y * 2.0))).r;
    vec2 maskGradient = vec2(mr - ml, mt - mb);
    vec2 refractedUv = clamp(blurUv + maskGradient * 0.012, vec2(0.0), vec2(1.0));
    vec3 refracted = texture(BlurSampler, refractedUv).rgb;
    refracted = adjustSaturation(refracted, screenParams.z);

    // Soft inner highlight from upper edge of mask.
    float highlight = clamp(mt - mb, 0.0, 1.0) * 0.6;
    refracted += vec3(highlight) * 0.25;

    // Mix with the original glassColor for predictable tint behaviour.
    glassColor = mix(glassColor, refracted, 0.55);

    vec3 result = mix(scene.rgb, glassColor, mask);
    fragColor = vec4(result, 1.0);
}
