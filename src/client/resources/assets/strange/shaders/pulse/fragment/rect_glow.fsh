#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 location, rectSize;
uniform vec4 glowColor;
uniform float glowRadius;
uniform float cornerRadius;

float dither(vec2 coord) {
    float noise = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    return (noise - 0.5) / 255.0;
}

float roundedRectSDF(vec2 pos, vec2 halfSize, float radius) {
    vec2 q = abs(pos) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    vec2 pos = texCoord * rectSize;
    vec2 center = rectSize * 0.5;
    vec2 halfSize = center;

    float dist = roundedRectSDF(pos - center, halfSize - glowRadius, cornerRadius);

    float alpha = 1.0 - smoothstep(0.0, glowRadius, dist);

    vec3 ditheredColor = glowColor.rgb + dither(gl_FragCoord.xy);
    fragColor = vec4(ditheredColor, glowColor.a * alpha);
}
