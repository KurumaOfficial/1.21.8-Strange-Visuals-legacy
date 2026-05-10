#version 330 core

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uBgTex;
uniform sampler2D uBlurTex;
uniform float uTime;
uniform int uMode;

// ---- Noise ----
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + vec2(1,0)), f.x),
        mix(hash21(i + vec2(0,1)), hash21(i + vec2(1,1)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) { v += a * noise(p); p *= 2.0; a *= 0.5; }
    return v;
}

// ---- Stars ----
float stars(vec2 uv, float density, float brightness) {
    vec2 cell = floor(uv * density);
    vec2 local = fract(uv * density);
    float star = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 nb = vec2(float(x), float(y));
            float rnd = hash21(cell + nb);
            vec2 pos = nb + rnd - local;
            float twinkle = sin(uTime * (rnd * 5.0 + 1.0)) * 0.3 + 0.7;
            star += smoothstep(0.06, 0.0, length(pos)) * brightness * twinkle;
        }
    }
    return star;
}

void main() {
    vec3 bg = texture(uBgTex, vUv).rgb;
    vec3 blurred = texture(uBlurTex, vUv).rgb;
    vec3 result;

    if (uMode == 0) {
        float t = uTime * 0.12;
        vec2 cuv = vUv * 3.0 + vec2(t * 0.3, t * 0.15);

        float n1 = fbm(cuv);
        float n2 = fbm(cuv * 1.5 + vec2(t * 0.2, -t * 0.1));

        vec3 neb = mix(vec3(0.05, 0.02, 0.2), vec3(0.02, 0.07, 0.26), n1);
        neb = mix(neb, vec3(0.15, 0.02, 0.13), n2 * 0.5);
        neb += vec3(0.05, 0.04, 0.12) * fbm(vUv * 5.0 + t);

        float sf = stars(vUv + t * 0.02, 35.0, 1.2)
                 + stars(vUv + t * 0.01, 70.0, 0.6)
                 + stars(vUv + t * 0.005, 140.0, 0.3);

        result = neb + vec3(sf);
        result = mix(result, blurred * 0.22 + bg * 0.08, 0.18);
    } else {
        vec2 warp = vUv + vec2(
            sin(vUv.y * 25.0 + uTime * 1.2) * 0.002,
            cos(vUv.x * 25.0 + uTime * 1.2) * 0.002
        );
        vec3 refracted = texture(uBlurTex, warp).rgb;
        result = mix(blurred, refracted, 0.45);

        result = mix(result, vec3(0.78, 0.85, 0.98), 0.1);
        result += noise(vUv * 80.0 + uTime * 0.4) * 0.035;
        float spec = pow(max(0.0, 1.0 - abs(vUv.y - 0.35) * 5.0), 4.0);
        result += vec3(0.65, 0.75, 1.0) * spec * 0.25;
    }

    fragColor = vec4(result, 1.0);
}
