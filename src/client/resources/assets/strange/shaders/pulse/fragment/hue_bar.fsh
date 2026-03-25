#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 location, rectSize;
uniform float radius;

float roundSDF(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b, 0.0)) - r;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec2 rectHalf = rectSize * 0.5;
    float r = min(radius, min(rectHalf.x, rectHalf.y));

    vec2 uv = texCoord * rectSize - rectHalf;

    float dist = roundSDF(uv, rectHalf - r, r);
    float alpha = 1.0 - smoothstep(-1.0, 1.0, dist);

    float hue = texCoord.s;
    vec3 color = hsv2rgb(vec3(hue, 1.0, 1.0));

    fragColor = vec4(color, alpha);
}
