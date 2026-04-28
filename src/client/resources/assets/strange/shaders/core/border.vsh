#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec2 UV1;
in vec2 RadiusXY;
in vec2 RadiusZW;
in vec2 BorderData1;
in vec2 BorderData2;

out vec2 FragCoord;
out vec4 FragColor;
out vec2 Size;
out vec4 Radius;
out float Thickness;
out vec2 Smoothness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    FragCoord = UV0;
    FragColor = Color;
    Size = UV1;
    Radius = vec4(RadiusXY, RadiusZW);
    Thickness = BorderData1.x;
    Smoothness = vec2(BorderData1.y, BorderData2.x);
}