#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 location, rectSize;
uniform vec4 color, outlineColor;
uniform float radius, thickness;

float roundedSDF(vec2 p, vec2 s, float rad) {
    return length(max(abs(p) - s + rad, 0.0)) - rad;
}

float roundSDF(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b, 0.0)) - r;
}

void main() {
    vec2 rectHalf = rectSize * 0.5;
    vec2 uv = texCoord * rectSize - rectHalf;

    float d = roundedSDF(uv, rectHalf - radius, radius);

    float alpha = smoothstep(0.0, 2.0, abs(d) / max(thickness * 0.1, 0.001) - 0.5);
    alpha = 1.0 - alpha;

    fragColor = vec4(outlineColor.rgb, alpha * outlineColor.a);
}
