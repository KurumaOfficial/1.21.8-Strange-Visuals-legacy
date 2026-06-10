#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;
in vec2 Size;
in vec4 Radius;
in float Smoothness;
in float BlurRadius;

uniform sampler2D Sampler0;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;
const int SAMPLES = 32;

float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);
    vec2 blurSize = texelSize * BlurRadius;

    vec3 result = texture(Sampler0, TexCoord).rgb;

    float step = TAU / float(SAMPLES);
    for (float angle = 0.0; angle < TAU; angle += step) {
        for (float dist = 0.2; dist <= 1.0; dist += 0.2) {
            vec2 offset = vec2(cos(angle), sin(angle)) * blurSize * dist;
            result += texture(Sampler0, TexCoord + offset).rgb;
        }
    }

    result /= float(SAMPLES * 5 + 1);

    vec2 center = Size * 0.5;
    vec2 pos = FragCoord * Size;
    float shapeDist = sdRoundBox(center - pos, center - 1.0, Radius);
    float alpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, shapeDist);

    vec4 color = ColorModulator * vec4(result, 1.0) * FragColor;
    color.a *= alpha;

    if (color.a == 0.0) {
        discard;
    }

    fragColor = color;
}