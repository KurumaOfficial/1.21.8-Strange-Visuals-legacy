#version 150

in vec2 TexCoord;
in float FogStart;
in float FogEnd;
in vec4 TargetFogColor; // Пришел из Vertex Shader (который взял его из .color())

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

out vec4 fragColor;

float linearizeDepth(float depth) {
    float near = 0.05;
    float far = 1024.0;
    float z = depth * 2.0 - 1.0;
    return (2.0 * near * far) / (far + near - z * (far - near));
}

void main() {
    float rawDepth = texture(Sampler1, TexCoord).r;
    float dist = linearizeDepth(rawDepth);

    // Формула тумана
    float fogFactor = (dist - FogStart) / (FogEnd - FogStart);
    fogFactor = clamp(fogFactor, 0.0, 1.0);

    if (fogFactor <= 0.01) {
        fragColor = texture(Sampler0, TexCoord);
        return;
    }

    // Блюр
    float radius = fogFactor * 8.0;
    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);

    vec3 blurredColor = vec3(0.0);
    float totalWeight = 0.0;

    for (float x = -2.0; x <= 2.0; x += 1.0) {
        for (float y = -2.0; y <= 2.0; y += 1.0) {
            vec2 offset = vec2(x, y) * radius * 0.4;
            blurredColor += texture(Sampler0, TexCoord + offset * texelSize).rgb;
            totalWeight += 1.0;
        }
    }
    blurredColor /= totalWeight;

    // Смешивание с TargetFogColor
    vec3 result = mix(blurredColor, TargetFogColor.rgb, fogFactor * TargetFogColor.a);

    fragColor = vec4(result, 1.0);
}