#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec2 UV1;
in vec2 RadiusXY;
in vec2 RadiusZW;
in vec2 BlurData;

out vec2 FragCoord;
out vec2 TexCoord;
out vec4 FragColor;
out vec2 Size;
out vec4 Radius;
out float Smoothness;
out float BlurRadius;

void main() {
    vec4 clipPos = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = clipPos;

    vec2 ndc = clipPos.xy / clipPos.w;
    TexCoord = ndc * 0.5 + 0.5;

    FragCoord = UV0;
    FragColor = Color;
    Size = UV1;
    Radius = vec4(RadiusXY, RadiusZW);
    Smoothness = BlurData.x;
    BlurRadius = BlurData.y;
}