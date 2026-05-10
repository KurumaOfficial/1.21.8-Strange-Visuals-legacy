#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;     // <-- Стандартный атрибут цвета
in vec2 UV0;
in vec2 UV1;       // <-- Сюда придут Start/End (так как мы использовали UV1_ELEMENT)

out vec2 TexCoord;
out float FogStart;
out float FogEnd;
out vec4 TargetFogColor; // Передаем во фрагментный шейдер

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vec2 ndc = gl_Position.xy / gl_Position.w;
    TexCoord = ndc * 0.5 + 0.5;

    // Просто пробрасываем данные дальше
    TargetFogColor = Color;
    FogStart = UV1.x;
    FogEnd = UV1.y;
}