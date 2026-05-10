#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(std140) uniform KawaseData {
    vec4 kawaseParams;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texelSize = 1.0 / vec2(kawaseParams.x, kawaseParams.y);
    float offset = kawaseParams.z;
    vec2 halfpixel = texelSize * offset;

    vec4 sum = texture(Sampler0, texCoord) * 4.0;
    sum += texture(Sampler0, texCoord - halfpixel);
    sum += texture(Sampler0, texCoord + halfpixel);
    sum += texture(Sampler0, texCoord + vec2(halfpixel.x, -halfpixel.y));
    sum += texture(Sampler0, texCoord - vec2(halfpixel.x, -halfpixel.y));

    fragColor = sum / 8.0;
}
