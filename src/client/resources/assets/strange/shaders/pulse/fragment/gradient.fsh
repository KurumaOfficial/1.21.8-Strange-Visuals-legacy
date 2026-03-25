#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 location, rectSize;
uniform vec4 radii;
uniform vec4 topLeftColor, topRightColor, bottomLeftColor, bottomRightColor;

float dither(vec2 coord) {
    float noise = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    return (noise - 0.5) / 255.0;
}

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 rectHalf = rectSize * 0.5;
    vec2 uv = texCoord;
    vec2 pos = uv * rectSize - rectHalf;

    vec4 topColor = mix(topLeftColor, topRightColor, uv.x);
    vec4 bottomColor = mix(bottomLeftColor, bottomRightColor, uv.x);
    vec4 finalColor = mix(topColor, bottomColor, uv.y);

    vec4 r = vec4(radii.y, radii.w, radii.x, radii.z);

    float dist = roundedBoxSDF(pos, rectHalf, r);
    float smoothedAlpha = 1.0 - smoothstep(-1.0, 1.0, dist);

    vec3 ditheredColor = finalColor.rgb + dither(gl_FragCoord.xy);
    fragColor = vec4(ditheredColor, finalColor.a * smoothedAlpha);
}
