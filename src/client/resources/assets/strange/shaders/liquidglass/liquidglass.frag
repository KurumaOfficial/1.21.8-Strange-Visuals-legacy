#version 330 core

in vec2 vLocalUv;

uniform sampler2D uScreenTex;
uniform sampler2D uBlurTex;
uniform vec2 uSize;
uniform vec4 uRadius;
uniform vec4 uScreenUV;
uniform vec2 uTexelSize;
uniform vec3 uTintColor;
uniform float uAlpha;

out vec4 fragColor;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 center = uSize * 0.5;
    vec2 halfSize = center - 1.0;
    vec2 localPos = vLocalUv * uSize - center;
    float userAlpha = clamp(uAlpha, 0.0, 1.0);
    float glassOpacity = 0.06 + pow(userAlpha, 0.9) * 0.54;

    float dist = roundedBoxSDF(localPos, halfSize, uRadius);
    float mask = 1.0 - smoothstep(-1.2, 1.2, dist);
    if (mask < 0.001) discard;

    vec2 screenUV = mix(uScreenUV.xy, uScreenUV.zw, vLocalUv);
    float insideDist = max(0.0, -dist);
    float edgeThin = 1.0 - smoothstep(0.0, 1.8, insideDist);
    float edgeWide = 1.0 - smoothstep(0.0, 8.0, insideDist);
    float edgeSoft = 1.0 - smoothstep(0.0, 16.0, insideDist);

    vec2 aspectDir = (vLocalUv - 0.5) * vec2(max(uSize.x / max(uSize.y, 1.0), 1.0), max(uSize.y / max(uSize.x, 1.0), 1.0));
    vec2 dir = normalize(aspectDir + vec2(1e-4));
    float waveA = sin(vLocalUv.y * 18.0 + vLocalUv.x * 6.0) * 0.5 + 0.5;
    float waveB = sin(vLocalUv.x * 24.0 - vLocalUv.y * 9.0) * 0.5 + 0.5;
    float liquid = (waveA * 0.55 + waveB * 0.45) * edgeSoft;
    vec2 refractOffset = dir * (1.1 + liquid * 2.1 + edgeWide * 1.1) * uTexelSize;
    vec2 refractUV = clamp(screenUV + refractOffset, uScreenUV.xy, uScreenUV.zw);
    vec3 blurred = texture(uBlurTex, screenUV).rgb;
    vec3 blurredDistorted = texture(uBlurTex, refractUV).rgb;
    vec3 sharpRefracted = texture(uScreenTex, refractUV).rgb;
    vec3 baseGlass = mix(blurred, blurredDistorted, 0.88);
    baseGlass = mix(baseGlass, sharpRefracted, 0.04 + edgeThin * 0.03);

    float luma = dot(baseGlass, vec3(0.299, 0.587, 0.114));
    baseGlass = mix(baseGlass, vec3(luma), 0.06);
    baseGlass *= 1.015;

    vec3 lightTint = mix(vec3(0.975, 0.985, 1.0), uTintColor, 0.12);
    vec3 brightBgTint = vec3(0.82, 0.86, 0.92);
    vec3 adaptiveTint = mix(lightTint, brightBgTint, smoothstep(0.72, 0.98, luma));
    float tintStrength = 0.028 + glassOpacity * 0.08;
    vec3 color = mix(baseGlass, adaptiveTint, tintStrength);

    float borderLine = edgeThin;
    color += vec3(1.0) * borderLine * 0.095;

    float innerGlow = 1.0 - smoothstep(0.0, 6.0, insideDist);
    color += vec3(1.0) * innerGlow * 0.030;

    float topLight = pow(1.0 - vLocalUv.y, 2.0) * 0.060;
    float leftLight = pow(1.0 - vLocalUv.x, 2.5) * 0.022;
    float edgeFade = edgeSoft;
    color += vec3(1.0) * (topLight + leftLight) * edgeFade;

    float ribbon1 = exp(-pow((vLocalUv.x - (0.18 + 0.06 * waveA)) * 10.0, 2.0));
    float ribbon2 = exp(-pow((vLocalUv.x - (0.74 - 0.04 * waveB)) * 14.0, 2.0));
    float ribbonMask = smoothstep(0.05, 0.55, 1.0 - vLocalUv.y);
    color += vec3(1.0) * ribbon1 * ribbonMask * edgeSoft * 0.070;
    color += vec3(1.0) * ribbon2 * ribbonMask * edgeSoft * 0.032;

    float causticBand = smoothstep(0.15, 0.55, waveA) * (1.0 - smoothstep(0.55, 0.95, waveA));
    causticBand += smoothstep(0.20, 0.60, waveB) * (1.0 - smoothstep(0.60, 0.95, waveB));
    color += vec3(1.0) * causticBand * edgeSoft * 0.018;

    float bottomDark = pow(vLocalUv.y, 3.0) * 0.04;
    float rightDark = pow(vLocalUv.x, 3.0) * 0.02;
    color -= vec3(1.0) * (bottomDark + rightDark) * edgeFade * 0.45;

    float finalAlpha = mask * clamp(glassOpacity + edgeThin * 0.085 + edgeWide * 0.028, 0.0, 0.76);
    fragColor = vec4(color, finalAlpha);
}