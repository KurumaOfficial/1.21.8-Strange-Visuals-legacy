#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

out vec2 texCoord;

void main() {
    vec2 pos;
    int vid = gl_VertexID;
    if (vid == 0) pos = vec2(-1.0, -1.0);
    else if (vid == 1) pos = vec2(1.0, -1.0);
    else if (vid == 2) pos = vec2(-1.0, 1.0);
    else if (vid == 3) pos = vec2(1.0, -1.0);
    else if (vid == 4) pos = vec2(1.0, 1.0);
    else pos = vec2(-1.0, 1.0);

    gl_Position = vec4(pos, 0.0, 1.0);
    texCoord = pos * 0.5 + 0.5;
}
