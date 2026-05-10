#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 FragCoord;
in vec4 FragColor;
in vec2 Size;
in vec4 Radius;
in float Thickness;
in vec2 Smoothness;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 center = Size * 0.5;
    vec2 pos = FragCoord * Size;

    float dist = sdRoundBox(center - pos, center - 1.0, Radius);

    float alpha = smoothstep(1.0 - Thickness - Smoothness.x - Smoothness.y,
    1.0 - Thickness - Smoothness.y, dist);
    alpha *= 1.0 - smoothstep(1.0 - Smoothness.y, 1.0, dist);

    vec4 color = ColorModulator * vec4(FragColor.rgb, FragColor.a * alpha);

    if (color.a == 0.0) {
        discard;
    }

    fragColor = color;
}