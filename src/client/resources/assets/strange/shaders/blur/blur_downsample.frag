#version 330 core
in vec2 vUv;
out vec4 FragColor;

uniform sampler2D uSource;
uniform vec2 uTexelSize;
uniform float uOffset;

vec4 tap(vec2 offset) {
    return texture(uSource, vUv + offset);
}

void main() {
    vec2 delta = uTexelSize * uOffset;
    vec2 dx = vec2(delta.x, 0.0);
    vec2 dy = vec2(0.0, delta.y);
    vec2 dxy = vec2(delta.x, delta.y);

    vec4 color = tap(vec2(0.0)) * 0.25;
    color += tap(dx) * 0.125;
    color += tap(-dx) * 0.125;
    color += tap(dy) * 0.125;
    color += tap(-dy) * 0.125;
    color += tap(dxy) * 0.0625;
    color += tap(vec2(-dxy.x, dxy.y)) * 0.0625;
    color += tap(vec2(dxy.x, -dxy.y)) * 0.0625;
    color += tap(-dxy) * 0.0625;

    FragColor = color;
}
