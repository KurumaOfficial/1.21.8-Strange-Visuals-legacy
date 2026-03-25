#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D BeforeSampler;
uniform sampler2D AfterSampler;
uniform sampler2D DepthBeforeSampler;
uniform sampler2D DepthAfterSampler;

layout(std140) uniform MaskData {
    vec4 maskParams;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(BeforeSampler, 0));

    vec4 colorBefore = texture(BeforeSampler, texCoord);
    vec4 colorAfter = texture(AfterSampler, texCoord);
    float depthBefore = texture(DepthBeforeSampler, texCoord).r;
    float depthAfter = texture(DepthAfterSampler, texCoord).r;

    vec3 colorDiff = abs(colorAfter.rgb - colorBefore.rgb);
    float colorDiffMag = max(max(colorDiff.r, colorDiff.g), colorDiff.b);

    float depthDiff = abs(depthAfter - depthBefore);

    float centerMask = smoothstep(0.0008, 0.02, colorDiffMag + depthDiff);

    float neighborMask = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y)) * texel;
            vec3 sampleBefore = texture(BeforeSampler, texCoord + offset).rgb;
            vec3 sampleAfter = texture(AfterSampler, texCoord + offset).rgb;
            float sampleDepthBefore = texture(DepthBeforeSampler, texCoord + offset).r;
            float sampleDepthAfter = texture(DepthAfterSampler, texCoord + offset).r;

            vec3 sampleDiff = abs(sampleAfter - sampleBefore);
            float sampleColor = max(max(sampleDiff.r, sampleDiff.g), sampleDiff.b);
            float sampleDepth = abs(sampleDepthAfter - sampleDepthBefore);
            neighborMask = max(neighborMask, smoothstep(0.0015, 0.03, sampleColor + sampleDepth));
        }
    }

    float mask = mix(centerMask, neighborMask, 0.45);
    mask = smoothstep(0.08, 0.92, mask);

    fragColor = vec4(mask, mask, mask, mask);
}
