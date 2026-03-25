#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 location, rectSize;
uniform float radius;
uniform vec4 shadowColor;

float dither(vec2 coord) {
    float noise = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    return (noise - 0.5) / 255.0;
}

void main() {
    vec2 rectHalf = rectSize * 0.5;
    vec2 center = rectHalf;
    vec2 pos = texCoord * rectSize;

    float distance = length(pos - center);
    float alpha = 1.0 - smoothstep(0.0, radius, distance);

    vec3 ditheredColor = shadowColor.rgb + dither(gl_FragCoord.xy);
    fragColor = vec4(ditheredColor, shadowColor.a * alpha);
}
