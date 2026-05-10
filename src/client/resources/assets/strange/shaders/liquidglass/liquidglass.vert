#version 330 core

layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUv;

uniform mat4 uModelViewMat;
uniform mat4 uProjMat;
uniform vec4 uRect;

out vec2 vLocalUv;

void main() {
    vec2 screenPos = uRect.xy + aUv * uRect.zw;
    gl_Position = uProjMat * uModelViewMat * vec4(screenPos, 0.0, 1.0);
    vLocalUv = aUv;
}