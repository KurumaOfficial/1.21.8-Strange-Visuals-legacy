#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D MaskTextureSampler;

layout(std140) uniform HudMaskData {
    vec4 screenParams;
    vec4 rectParams;
    vec4 maskParams;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 screenPos = vec2(
        texCoord.x * screenParams.x,
        (1.0 - texCoord.y) * screenParams.y
    );

    vec2 rectMin = rectParams.xy;
    vec2 rectSize = max(rectParams.zw, vec2(1.0));
    vec2 localUv = (screenPos - rectMin) / rectSize;

    if (localUv.x < 0.0 || localUv.x > 1.0 || localUv.y < 0.0 || localUv.y > 1.0) {
        fragColor = vec4(0.0);
        return;
    }

    float maskAlpha = texture(MaskTextureSampler, localUv).a * clamp(maskParams.x, 0.0, 1.0);
    fragColor = vec4(maskAlpha, maskAlpha, maskAlpha, maskAlpha);
}
