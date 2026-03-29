#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;        // FragCoord (0-1 координаты внутри прямоугольника)
in vec4 Color;
in vec2 UV1;        // Size
in vec2 RadiusXY;   // radius1, radius2
in vec2 RadiusZW;   // radius3, radius4
in vec2 ExtraData1; // Smoothness, CornerSmoothness
in vec2 ExtraData2; // FresnelPower, DistortStrength
in vec2 ScreenUV;   // Текстурные координаты для размытой текстуры
in vec2 CenterUV;   // Центр UV координат (константа для всех вершин)

out vec2 FragCoord;
out vec2 TexCoord;
out vec2 CenterUVOut;  // Центр UV координат для правильного искажения
out vec4 FragColor;
out vec2 Size;
out vec4 Radius;
out float Smoothness;
out float CornerSmoothness;
out float FresnelPower;
out float DistortStrength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    
    FragCoord = UV0;           // 0-1 координаты для SDF
    TexCoord = ScreenUV;       // Координаты для текстуры размытия
    CenterUVOut = CenterUV;    // Центр UV координат (константа)
    FragColor = Color;
    Size = UV1;
    Radius = vec4(RadiusXY, RadiusZW);
    Smoothness = ExtraData1.x;
    CornerSmoothness = max(ExtraData1.y, 2.0);
    FresnelPower = ExtraData2.x;
    DistortStrength = ExtraData2.y;
}
