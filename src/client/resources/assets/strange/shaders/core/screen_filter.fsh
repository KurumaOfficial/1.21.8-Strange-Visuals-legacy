#version 150

uniform sampler2D SceneSampler;

layout(std140) uniform FilterData {
    vec4 primary;
    vec4 secondary;
    vec4 tertiary;
    vec4 tintColor;
};

in vec2 texCoord;

out vec4 fragColor;

vec3 applySaturation(vec3 color, float saturation) {
    float grey = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(grey), color, saturation);
}

float noise(vec2 uv) {
    return fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 safeSceneUv(vec2 uv) {
    vec2 texSize = vec2(textureSize(SceneSampler, 0));
    vec2 texel = 0.5 / max(texSize, vec2(1.0));
    return clamp(uv, texel, vec2(1.0) - texel);
}

void main() {
    vec2 uv = safeSceneUv(texCoord);
    vec4 scene = texture(SceneSampler, uv);
    vec3 color = scene.rgb;

    color += vec3(primary.z);
    color = (color - 0.5) * primary.w + 0.5;
    color = applySaturation(color, secondary.x);
    color = 1.0 - exp(-max(color, vec3(0.0)) * secondary.y);
    color = pow(max(color, vec3(0.0)), vec3(1.0 / max(secondary.z, 0.01)));

    if (tertiary.y > 0.0) {
        color = mix(color, tintColor.rgb, tertiary.y * tintColor.a);
    }

    if (secondary.w > 0.0) {
        vec2 centered = uv * 2.0 - 1.0;
        float vignette = 1.0 - dot(centered, centered) * secondary.w * 0.55;
        color *= clamp(vignette, 0.0, 1.0);
    }

    if (tertiary.x > 0.0) {
        float grain = (noise(gl_FragCoord.xy + tertiary.z * 60.0) - 0.5) * tertiary.x * 0.08;
        color += vec3(grain);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), scene.a);
}
