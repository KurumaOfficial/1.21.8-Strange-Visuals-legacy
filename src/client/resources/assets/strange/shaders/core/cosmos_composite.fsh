#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D SceneAfterSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform CosmosData {
    vec4 screenParams;
    vec4 cosmosParams;
    vec4 nebulaColor;
    vec4 accentColor;
    vec4 themeParams;
    vec4 pad;
};

in vec2 texCoord;

out vec4 fragColor;

mat2 rot2(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec2 hash2(vec2 p) {
    return vec2(
        fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453),
        fract(sin(dot(p, vec2(269.5, 183.3))) * 43758.5453)
    );
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    mat2 rotation = rot2(0.55);
    for (int i = 0; i < 5; i++) {
        value += amplitude * noise(p);
        p = rotation * p * 2.0 + vec2(0.17, -0.11);
        amplitude *= 0.5;
    }
    return value;
}

float voronoi(vec2 p) {
    vec2 n = floor(p);
    vec2 f = fract(p);
    float minDistance = 10.0;

    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash2(n + g);
            vec2 r = g + o - f;
            float d = dot(r, r);
            minDistance = min(minDistance, d);
        }
    }

    return minDistance;
}

float band(float value, float width) {
    return smoothstep(width, 0.0, abs(value));
}

vec2 centerUv(vec2 uv) {
    return vec2(uv.x * screenParams.x / screenParams.y - 0.5 * screenParams.x / screenParams.y, uv.y - 0.5);
}

float starField(vec2 uv, float density, float time, float speed, float layer, float sparkle) {
    float stars = 0.0;
    vec2 drift = vec2(time * speed * (0.06 + layer * 0.025), time * speed * 0.018);
    vec2 p = (uv + drift) * max(density, 0.001) * (1.0 + layer * 0.45);
    vec2 cell = floor(p);
    vec2 f = fract(p);
    float scl = max(density, 0.001) / max(screenParams.y, 1.0);

    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 nb = vec2(float(dx), float(dy));
            vec2 id = cell + nb;
            vec2 jitter = hash2(id + layer * 31.71);
            vec2 diff = nb + jitter - f;
            float d = length(diff);

            float thresh = 0.50 + layer * 0.07;
            float appear = step(thresh, hash(id + layer * 17.3));
            float targetPx = mix(2.0, 1.0, hash(id + layer * 9.1));
            float sz = targetPx * scl;
            float core = smoothstep(sz, 0.0, d) * appear;

            float twinkle = 0.55 + 0.45 * sin(time * (1.8 + hash(id) * 5.0) + hash(id + 2.7) * 6.2831);
            stars += core * mix(0.45, twinkle, sparkle);
        }
    }

    return stars;
}

vec3 softNebula(vec2 uv, float time, float intensity, vec3 baseColor, vec3 accent) {
    float n1 = fbm(uv * 2.5 + time * 0.035);
    float n2 = fbm(uv * 5.0 - time * 0.022 + vec2(5.2, 1.3));
    float n3 = fbm(uv * 2.0 + time * 0.016 + vec2(1.7, 9.2));

    vec3 c1 = baseColor;
    vec3 c2 = mix(baseColor, accent, 0.55);
    vec3 c3 = baseColor * vec3(0.12, 0.09, 0.22);

    vec3 col = mix(c3, c1, smoothstep(0.28, 0.72, n1));
    col = mix(col, c2, smoothstep(0.42, 0.82, n2) * 0.55);
    col += accent * smoothstep(0.52, 0.92, n3) * 0.24;
    return col * intensity;
}

vec3 auroraTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    float bands = sin((uv.x * 4.0 + fbm(uv * 2.5 + time * 0.18) * 1.4) * scale - time * 0.7);
    float curtain = smoothstep(0.18, 0.92, bands * 0.5 + 0.5);
    float shimmer = fbm(vec2(uv.x * 3.2, uv.y * 8.5 - time * 0.22));
    vec3 col = mix(base * 0.35, accent, curtain);
    return col * (0.25 + 0.9 * curtain) * (0.45 + shimmer * 0.75) * intensity;
}

vec3 lavaTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (3.6 * scale);
    float flow = fbm(p + vec2(time * 0.35, -time * 0.18));
    float cracks = smoothstep(0.58, 0.82, flow);
    float embers = starField(uv + vec2(time * 0.015, 0.0), 18.0, time, 0.15, 0.0, 0.65);
    vec3 molten = mix(base * 0.22, accent, cracks);
    molten += accent * embers * 0.65;
    return molten * intensity;
}

vec3 oceanTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (4.2 * scale);
    float waves = sin((p.x + time * 0.35) * 2.2 + fbm(p + vec2(0.0, time * 0.12)) * 2.5);
    float caustics = smoothstep(0.62, 0.94, 0.5 + 0.5 * waves);
    float fog = fbm(p * 0.7 - vec2(time * 0.08, time * 0.05));
    vec3 col = mix(base * 0.20, base, fog * 0.7);
    col += accent * caustics * 0.85;
    return col * intensity;
}

vec3 matrixTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * vec2(18.0, 34.0) * scale;
    vec2 cell = floor(p);
    vec2 local = fract(p) - 0.5;
    float columnSeed = hash(vec2(cell.x, 7.0));
    float stream = fract(-time * (0.95 + columnSeed * 0.85) + columnSeed * 7.0);
    float body = band(local.x, 0.18);
    float head = band(local.y + 0.5 - stream, 0.10);
    float trail = smoothstep(0.62, 0.0, local.y + 0.5 - stream + 0.38);
    float glyph = step(0.42, hash(cell + vec2(floor(time * 13.0), floor(time * 7.0))));
    float scan = 0.72 + 0.28 * sin((uv.y - time * 0.35) * 180.0);

    vec3 col = base * body * trail * (0.25 + glyph * 0.75) * scan;
    col += accent * body * head * 1.85;
    col += accent * body * glyph * 0.14;
    return col * intensity;
}

vec3 toxicTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (3.4 * scale);
    float fumes = fbm(p + vec2(time * 0.18, -time * 0.12));
    float bubbles = smoothstep(0.72, 0.95, fbm(p * 1.6 - time * 0.25));
    vec3 col = mix(base * 0.18, base, fumes);
    col += accent * bubbles * 0.95;
    return col * intensity;
}

vec3 thunderTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (4.8 * scale);
    float cloud = fbm(p - vec2(time * 0.12, time * 0.08));
    float bolt = abs(sin((p.x + fbm(p + time * 0.15) * 2.5) * 2.8 - time * 3.2));
    bolt = smoothstep(0.88, 0.99, bolt);
    vec3 col = mix(base * 0.15, base, cloud * 0.85);
    col += accent * bolt * 1.65;
    return col * intensity;
}

vec3 crystalTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (7.0 * scale);
    float cells = voronoi(p + time * 0.05);
    float facets = smoothstep(0.08, 0.0, abs(cells - 0.18));
    float shine = smoothstep(0.82, 0.98, fbm(p * 0.7 + time * 0.18));
    vec3 col = mix(base * 0.18, base, 1.0 - cells);
    col += accent * (facets * 0.85 + shine * 0.65);
    return col * intensity;
}

vec3 sunsetTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    float horizon = smoothstep(-0.2, 0.8, 1.0 - uv.y);
    float clouds = fbm(vec2(uv.x * 3.0 * scale + time * 0.08, uv.y * 2.2));
    float gold = smoothstep(0.62, 0.88, clouds + horizon * 0.25);
    vec3 warm = mix(base, accent, horizon);
    return (warm * (0.35 + clouds * 0.65) + accent * gold * 0.55) * intensity;
}

vec3 amberTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (3.1 * scale);
    float resin = fbm(p + vec2(time * 0.06, -time * 0.04));
    float dust = starField(uv, 12.0, time, 0.06, 0.0, 0.4);
    vec3 col = mix(base * 0.16, base, resin);
    col += accent * dust * 0.7;
    return col * intensity;
}

vec3 neonTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (10.0 * scale);
    float gridX = band(fract(p.x) - 0.5, 0.12);
    float gridY = band(fract(p.y + time * 0.15) - 0.5, 0.12);
    float scan = 0.55 + 0.45 * sin((uv.y + time * 0.25) * 60.0);
    vec3 col = base * (gridX + gridY) * 0.28;
    col += accent * max(gridX, gridY) * (0.7 + scan * 0.35);
    return col * intensity;
}

vec3 voidTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = centerUv(uv);
    float angle = atan(p.y, p.x);
    float radius = length(p) * 3.0;
    float swirl = fbm(vec2(angle * 1.4, radius * 1.8 - time * 0.22) * scale);
    float rift = smoothstep(0.74, 0.96, sin(angle * 5.0 + time * 0.8 + swirl * 4.5));
    vec3 col = mix(base * 0.10, accent * 0.7, swirl * 0.65);
    col += accent * rift * 0.45;
    return col * intensity;
}

vec3 sakuraTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (6.2 * scale);
    float cloud = fbm(p + vec2(time * 0.05, -time * 0.04));
    float petals = starField(uv + vec2(-time * 0.03, time * 0.02), 14.0, time, 0.08, 0.0, 0.55);
    vec3 col = mix(base * 0.18, base, cloud);
    col += accent * petals * 0.95;
    return col * intensity;
}

vec3 cobwebTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = centerUv(uv) * (2.8 * scale);
    float radius = length(p);
    float angle = atan(p.y, p.x);
    float wobble = fbm(vec2(angle * 1.8, radius * 4.4 - time * 0.12));
    float spokes = smoothstep(0.08, 0.0, abs(sin(angle * 8.0 + wobble * 1.8)));
    float rings = smoothstep(0.10, 0.0, abs(fract(radius * 5.5 - wobble * 0.35) - 0.5));
    float haze = smoothstep(0.42, 0.88, fbm(p * 1.7 + vec2(time * 0.03, -time * 0.02)));

    vec3 col = base * (spokes * 0.46 + rings * 0.34);
    col += accent * spokes * rings * 1.4;
    col += accent * haze * 0.10;
    return col * intensity;
}

vec3 glacierTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (6.4 * scale);
    float cells = voronoi(p + fbm(p * 0.6) * 0.45);
    float cracks = smoothstep(0.09, 0.0, abs(cells - 0.16));
    float frost = smoothstep(0.55, 0.92, fbm(rot2(0.7) * p * 1.25 - vec2(time * 0.08, 0.0)));
    float wind = smoothstep(0.76, 0.98, sin((uv.y + fbm(p) * 0.12) * 26.0 - time * 0.9) * 0.5 + 0.5);

    vec3 col = mix(base * 0.18, base, 1.0 - cells * 0.85);
    col += accent * (cracks * 1.15 + frost * 0.45 + wind * 0.20);
    return col * intensity;
}

vec3 solarisTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = centerUv(uv) * (4.0 * scale);
    float radius = length(p);
    float angle = atan(p.y, p.x);
    float core = smoothstep(1.05, 0.10, radius);
    float flare = smoothstep(0.58, 0.96, sin(angle * 6.0 - time * 1.55 + fbm(p * 2.4) * 3.0) * 0.5 + 0.5);
    float convection = smoothstep(0.42, 0.86, fbm(p * 3.1 - time * 0.35));

    vec3 col = base * (core * 0.62 + convection * 0.42);
    col += accent * (flare * (1.0 - core) * 1.3 + core * 0.92);
    return col * intensity;
}

vec3 fractalTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 z = centerUv(uv) * (3.2 * scale);
    float accum = 0.0;
    float glow = 0.0;

    for (int i = 0; i < 4; i++) {
        z = abs(z) / clamp(dot(z, z), 0.25, 2.8) - vec2(0.78, 0.62);
        z *= rot2(0.65 + time * 0.05);
        accum += exp(-3.4 * abs(length(z) - 0.75));
        glow += exp(-8.0 * abs(z.x * z.y));
    }

    vec3 col = base * accum * 0.24;
    col += accent * glow * 0.24;
    col += mix(base, accent, 0.5) * smoothstep(0.58, 0.95, fbm(z * 2.0 + time * 0.1)) * 0.28;
    return col * intensity;
}

vec3 eclipseTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = centerUv(uv) * (3.0 * scale);
    float radius = length(p * vec2(1.0, 1.08));
    float shadow = smoothstep(0.34, 0.08, radius);
    float ring = smoothstep(0.08, 0.0, abs(radius - 0.38));
    float corona = smoothstep(0.42, 0.88, fbm(vec2(atan(p.y, p.x) * 1.7, radius * 6.0 - time * 0.18)));
    corona *= smoothstep(0.92, 0.24, radius);

    vec3 col = base * (0.16 + corona * 0.55);
    col += accent * (ring * 1.75 + corona * 0.55);
    col *= (1.0 - shadow * 0.9);
    return col * intensity;
}

vec3 circuitTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (12.0 * scale);
    vec2 cell = floor(p);
    vec2 f = fract(p) - 0.5;
    float h = band(f.y, 0.06) * step(0.55, hash(cell + vec2(1.3, 0.0)));
    float v = band(f.x, 0.06) * step(0.55, hash(cell + vec2(0.0, 2.7)));
    float node = smoothstep(0.18, 0.0, length(f)) * step(0.72, hash(cell + vec2(4.1, 5.7)));
    float pulse = smoothstep(0.28, 0.0, abs(fract(time * 0.7 + cell.x * 0.17 + cell.y * 0.11) - (f.x + 0.5)));

    vec3 col = base * (h + v) * 0.55;
    col += accent * (node * 1.2 + max(h, v) * pulse * 0.95);
    return col * intensity;
}

vec3 coralTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (4.0 * scale);
    float growth = fbm(p + vec2(time * 0.05, -time * 0.03));
    float polyps = smoothstep(0.72, 0.95, fbm(p * 2.4 + growth * 1.2));
    float tide = smoothstep(0.58, 0.92, fbm(rot2(0.8) * p * 1.6 - vec2(time * 0.12, 0.0)));

    vec3 col = mix(base * 0.20, base, growth);
    col += accent * (polyps * 0.95 + tide * 0.28);
    return col * intensity;
}

vec3 prismTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = centerUv(uv) * (5.2 * scale);
    float facets = smoothstep(0.10, 0.0, abs(voronoi(p + time * 0.05) - 0.16));
    float dispersion = smoothstep(0.35, 0.92, fbm(p * 1.3 + time * 0.08));
    vec3 spectral = vec3(
        smoothstep(0.12, 0.98, sin(p.x * 1.2 + dispersion * 4.2) * 0.5 + 0.5),
        smoothstep(0.12, 0.98, sin(p.x * 1.2 + 2.1 + dispersion * 4.2) * 0.5 + 0.5),
        smoothstep(0.12, 0.98, sin(p.x * 1.2 + 4.2 + dispersion * 4.2) * 0.5 + 0.5)
    );

    vec3 col = mix(base * 0.20, accent, facets * 0.35);
    col += spectral * (facets * 0.92 + dispersion * 0.22);
    return col * intensity;
}

vec3 monsoonTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * scale;
    float rain = smoothstep(0.05, 0.0, abs(fract((uv.x + fbm(p * 3.2) * 0.08 + time * 0.18) * 18.0) - 0.5));
    float streak = smoothstep(0.34, 0.0, abs(fract((uv.y * 1.7 - time * 1.4 + fbm(p * 2.0) * 0.16) * 8.0) - 0.5));
    float clouds = fbm(uv * (4.4 * scale) - vec2(time * 0.12, time * 0.05));
    float flash = smoothstep(0.92, 0.99, sin((uv.x + uv.y) * 14.0 - time * 2.8) * 0.5 + 0.5);

    vec3 col = mix(base * 0.18, base, clouds);
    col += accent * (rain * streak * 0.95 + flash * 0.45);
    return col * intensity;
}

vec3 bloomTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = centerUv(uv) * (3.2 * scale);
    float radius = length(p);
    float angle = atan(p.y, p.x);
    float petals = smoothstep(0.48, 0.94, sin(angle * 6.0 + fbm(p * 2.4) * 2.0 - time * 0.18) * 0.5 + 0.5);
    petals *= smoothstep(0.92, 0.10, radius);
    float pollen = smoothstep(0.72, 0.95, fbm(p * 5.0 + time * 0.12));

    vec3 col = base * (petals * 0.48 + pollen * 0.22);
    col += accent * (petals * 0.96 + pollen * 0.35);
    return col * intensity;
}

float pulseRandom(vec2 pos) {
    return fract(sin(dot(pos.xy, vec2(13.9898, 78.233))) * 43758.5453123);
}

float pulseNoise(vec2 pos) {
    vec2 i = floor(pos);
    vec2 f = fract(pos);
    float a = pulseRandom(i + vec2(0.0, 0.0));
    float b = pulseRandom(i + vec2(1.0, 0.0));
    float c = pulseRandom(i + vec2(0.0, 1.0));
    float d = pulseRandom(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float pulseFbm(vec2 pos, float t) {
    float value = 0.0;
    float amplitude = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rotation = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 5; i++) {
        float dir = mod(float(i), 2.0) > 0.5 ? 1.0 : -1.0;
        value += amplitude * pulseNoise(pos + dir * t * 0.3);
        pos = rotation * pos * 2.0 + shift;
        amplitude *= 0.5;
    }
    return value;
}

vec3 pulseNebulaTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = centerUv(uv) * (6.0 * scale);
    vec2 q = vec2(
        pulseFbm(p + vec2(0.0, 0.0), time * 0.8),
        pulseFbm(p + vec2(1.0, 1.0), time * 0.6)
    );
    vec2 r = vec2(
        pulseFbm(p + q + vec2(1.7, 1.2), time * 0.7),
        pulseFbm(p + q + vec2(8.3, 2.8), time * 0.9)
    );
    float f = pulseFbm(p + r, time);

    vec3 c1 = mix(base * 1.25, accent, 0.18);
    vec3 c2 = base * 0.52;
    vec3 c3 = vec3(base.r * 0.42, base.g * 0.28, accent.b);

    vec3 color = mix(c1, c2, clamp(f * f * 4.0, 0.0, 1.0));
    color = mix(color, c1, clamp(length(q), 0.0, 1.0));
    color = mix(color, c3, clamp(abs(r.x), 0.0, 1.0));
    color = (f * f * f + 0.6 * f * f + 0.5 * f) * color;
    return clamp(color * 1.75 * intensity, 0.0, 1.0);
}

float pulseField(vec3 p, float time) {
    float strength = 7.0 + 0.03 * log(1e-6 + fract(sin(time) * 4373.11));
    float accum = 0.0;
    float prev = 0.0;
    float tw = 0.0;

    float mag = dot(p, p);
    p = abs(p) / max(mag, 0.001) + vec3(-0.5, -0.8 + 0.1 * sin(time * 0.7 + 2.0), -1.1 + 0.3 * cos(time * 0.3));
    float w = exp(0.0);
    accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
    tw += w;

    return max(0.0, 5.0 * accum / max(tw, 0.001) - 0.7);
}

vec3 pulseStarfieldTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 screenUv = centerUv(uv) * vec2(2.0, 2.0);

    float speed = 0.01 * cos(time * 0.02 + 3.1415926 / 4.0);
    const float formuparam = 0.79;
    const int iterations = 14;
    const int volsteps = 5;
    const float stepsize = 0.390;
    const float zoom = 0.900;
    const float tile = 0.850;
    const float brightness = 0.003;
    const float distfading = 0.560;
    const float saturation = 0.800;
    const float transverseSpeed = 1.8;
    const float cloud = 0.11;

    float a_xz = 0.9;
    float a_yz = -0.6;
    float a_xy = 0.9 + time * 0.08;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));

    vec3 dir = vec3(screenUv * zoom * scale, 1.0);
    vec3 from = vec3(0.0);
    vec3 forward = vec3(0.0, 0.0, 1.0);

    from.x += transverseSpeed * cos(0.01 * time) + 0.001 * time;
    from.y += transverseSpeed * sin(0.01 * time) + 0.001 * time;
    from.z += 0.003 * time;

    dir.xy *= rot_xy;
    forward.xy *= rot_xy;
    dir.xz *= rot_xz;
    forward.xz *= rot_xz;
    dir.yz *= rot_yz;
    forward.yz *= rot_yz;

    from.xy *= -rot_xy;
    from.xz *= rot_xz;
    from.yz *= rot_yz;

    float zooom = (time - 3311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, stepsize);
    float zoffset = -sampleShift;
    sampleShift /= stepsize;

    float s = 0.24;
    float s3 = s + stepsize / 2.0;
    vec3 volume = vec3(0.0);
    vec3 cloudColor = vec3(0.0);

    for (int r = 0; r < volsteps; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = from + (s3 + zoffset) * dir;

        p2 = abs(vec3(tile) - mod(p2, vec3(tile * 2.0)));
        p3 = abs(vec3(tile) - mod(p3, vec3(tile * 2.0)));
        float t3 = pulseField(p3, time);

        float pa = 0.0;
        float a = 0.0;
        for (int i = 0; i < iterations; i++) {
            p2 = abs(p2) / max(dot(p2, p2), 0.001) - formuparam;
            float dist = abs(length(p2) - pa);
            a += i > 7 ? min(12.0, dist) : dist;
            pa = length(p2);
        }

        a *= a * a;
        float fade = pow(distfading, max(0.0, float(r) - sampleShift));
        if (r == 0) fade *= 1.0 - sampleShift;
        if (r == volsteps - 1) fade *= sampleShift;

        float s1 = s + zoffset;
        volume += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * brightness * fade;
        cloudColor += mix(0.11, 1.0, 1.0) * vec3(1.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;

        s += stepsize;
        s3 += stepsize;
    }

    volume = mix(vec3(length(volume)), volume, saturation);
    cloudColor *= cloud;
    cloudColor.b *= 1.8;
    cloudColor.r *= 0.05;
    cloudColor.b = 0.5 * mix(cloudColor.g, cloudColor.b, 0.8);
    cloudColor.g = 0.0;
    cloudColor.bg = mix(cloudColor.gb, cloudColor.bg, 0.5 * (cos(time * 0.01) + 1.0));

    vec3 result = volume * 0.01 + cloudColor;
    result = mix(result, result * mix(base, accent, 0.38) * 2.0, 0.5);
    return clamp(result * intensity, 0.0, 1.0);
}

float pulseCobwebDistance(vec3 p, float time) {
    const float sq = 0.70710678;
    vec3 q = abs(mod(p + vec3(cos(p.z * 0.5), cos(p.x * 0.5), cos(p.y * 0.5)), 2.0) - 1.0);
    float a = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    q = vec3(p.x + p.y, p.y + p.z, p.z + p.x) * sq;
    q = abs(mod(q, 2.0) - 1.0);
    float b = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    return min(a, b);
}

vec3 pulseCobwebNormal(vec3 p, float time) {
    vec3 eps = vec3(0.001, 0.0, 0.0);
    float o = pulseCobwebDistance(p, time);
    return normalize(o - vec3(
        pulseCobwebDistance(p - eps, time),
        pulseCobwebDistance(p - eps.zxy, time),
        pulseCobwebDistance(p - eps.yzx, time)
    ));
}

vec3 pulseCobwebTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    float aspect = screenParams.x / max(screenParams.y, 1.0);
    vec2 p = uv * 2.0 - 1.0;
    vec2 motion = vec2(sin(time * 0.3) * 0.3, cos(time * 0.2) * 0.3);
    p.x *= aspect;
    motion.x *= aspect;

    vec3 origin = vec3(0.0, 0.0, time * 0.5);
    vec3 shift = vec3(motion, 0.0);
    vec3 ray = vec3(p * scale, 1.0) / 32.0;
    vec3 trace = vec3(0.0);
    vec3 shade = vec3(0.5);

    for (int i = 0; i < 64; i++) {
        float dist = pulseCobwebDistance(trace + shift + origin, time);
        trace += dist * 10.0 * ray;
        shade += dist;
    }

    shade /= 64.0;
    vec3 normal = pulseCobwebNormal(trace + shift + origin, time);
    float spec = dot(normal, shade);
    vec3 color = (shade + pow(spec, 4.0)) * (1.0 - shade * 0.01) * mix(base, accent, 0.18);
    color *= trace.z * 0.125;

    vec2 vignette = p * 0.43;
    vignette.y *= aspect;
    float vigAmount = max(0.0, 1.0 - length(vignette) * 0.5);
    return clamp(color * 3.0 * vigAmount * intensity, 0.0, 1.0);
}

vec3 pulseHsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 pulsePlasmaTheme(vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    vec2 p = uv * (10.0 * scale);
    float plasma = 0.0;
    plasma += sin(p.x + time * 0.8);
    plasma += sin((p.y + time * 0.8) * 0.5);
    plasma += sin((p.x + p.y + time * 0.8) * 0.5);

    vec2 c1 = p - vec2(5.0 * scale);
    plasma += sin(sqrt(c1.x * c1.x + c1.y * c1.y + 1.0) + time * 0.8);

    vec2 c2 = p - vec2(5.0 + sin(time * 0.4) * 3.0, 5.0 + cos(time * 0.56) * 3.0);
    plasma += sin(sqrt(c2.x * c2.x + c2.y * c2.y + 1.0) + time * 1.2);
    plasma *= 0.2;

    float hue = plasma + time * 0.1;
    vec3 rainbow = pulseHsv2rgb(vec3(hue, 0.8, 1.0));
    float flowHue = hue * 0.35 + base.r * 0.25 + accent.b * 0.15;
    float flowSat = 0.7 + sin(plasma * 3.14159) * 0.3;
    float flowVal = 0.8 + plasma * 0.4;

    vec3 color = pulseHsv2rgb(vec3(flowHue, flowSat, flowVal));
    color = mix(color, rainbow, 0.38);
    float glow = pow(abs(sin(plasma * 3.14159 * 2.0)), 2.0) * 0.5;
    color += mix(base, accent, 0.3) * glow;
    color *= 0.8 + sin(time * 2.0 + plasma * 5.0) * 0.2;

    return clamp(color * intensity, 0.0, 1.0);
}

vec3 themedField(float theme, vec2 uv, float time, vec3 base, vec3 accent, float scale, float intensity) {
    if (theme < 0.5) return softNebula(uv, time, intensity, base, accent);
    if (theme < 1.5) return auroraTheme(uv, time, base, accent, scale, intensity);
    if (theme < 2.5) return lavaTheme(uv, time, base, accent, scale, intensity);
    if (theme < 3.5) return oceanTheme(uv, time, base, accent, scale, intensity);
    if (theme < 4.5) return matrixTheme(uv, time, base, accent, scale, intensity);
    if (theme < 5.5) return toxicTheme(uv, time, base, accent, scale, intensity);
    if (theme < 6.5) return thunderTheme(uv, time, base, accent, scale, intensity);
    if (theme < 7.5) return crystalTheme(uv, time, base, accent, scale, intensity);
    if (theme < 8.5) return sunsetTheme(uv, time, base, accent, scale, intensity);
    if (theme < 9.5) return amberTheme(uv, time, base, accent, scale, intensity);
    if (theme < 10.5) return neonTheme(uv, time, base, accent, scale, intensity);
    if (theme < 11.5) return voidTheme(uv, time, base, accent, scale, intensity);
    if (theme < 12.5) return sakuraTheme(uv, time, base, accent, scale, intensity);
    if (theme < 13.5) return cobwebTheme(uv, time, base, accent, scale, intensity);
    if (theme < 14.5) return glacierTheme(uv, time, base, accent, scale, intensity);
    if (theme < 15.5) return solarisTheme(uv, time, base, accent, scale, intensity);
    if (theme < 16.5) return fractalTheme(uv, time, base, accent, scale, intensity);
    if (theme < 17.5) return eclipseTheme(uv, time, base, accent, scale, intensity);
    if (theme < 18.5) return circuitTheme(uv, time, base, accent, scale, intensity);
    if (theme < 19.5) return coralTheme(uv, time, base, accent, scale, intensity);
    if (theme < 20.5) return prismTheme(uv, time, base, accent, scale, intensity);
    if (theme < 21.5) return monsoonTheme(uv, time, base, accent, scale, intensity);
    if (theme < 22.5) return bloomTheme(uv, time, base, accent, scale, intensity);
    if (theme < 23.5) return pulseNebulaTheme(uv, time, base, accent, scale, intensity);
    if (theme < 24.5) return pulseStarfieldTheme(uv, time, base, accent, scale, intensity);
    if (theme < 25.5) return pulseCobwebTheme(uv, time, base, accent, scale, intensity);
    return pulsePlasmaTheme(uv, time, base, accent, scale, intensity);
}

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    float mask = texture(MaskSampler, texCoord).r;

    if (mask < 0.01) {
        fragColor = scene;
        return;
    }

    float time = screenParams.z;
    float starDensity = screenParams.w;
    float starSpeed = cosmosParams.x;
    float nebulaIntens = cosmosParams.y;
    float edgeGlow = cosmosParams.z;
    float theme = cosmosParams.w;
    float patternScale = themeParams.x;
    float sparkle = themeParams.y;
    float starMix = themeParams.z;
    float pulseAlpha = themeParams.w;
    float pulseEffectOnly = pad.x;

    vec3 baseColor = nebulaColor.rgb;
    vec3 accent = accentColor.rgb;

    vec2 uv = texCoord;
    uv.x *= screenParams.x / screenParams.y;

    vec3 field = themedField(theme, uv, time, baseColor, accent, patternScale, nebulaIntens);
    vec3 cosmos = mix(vec3(0.003, 0.002, 0.012), baseColor * 0.08, 0.55) + field;

    float s0 = starField(uv, starDensity, time, starSpeed, 0.0, sparkle) * starMix;
    float s1 = starField(uv, starDensity * 1.55, time, starSpeed * 0.68, 1.0, sparkle * 0.85) * starMix;
    float s2 = starField(uv, starDensity * 2.35, time, starSpeed * 0.42, 2.0, sparkle * 0.72) * starMix;

    cosmos += mix(vec3(1.0, 0.97, 1.0), accent, 0.18) * s0 * 1.25;
    cosmos += mix(baseColor, accent, 0.45) * s1 * 0.72;
    cosmos += mix(baseColor, vec3(1.0), 0.25) * s2 * 0.36;

    if (edgeGlow > 0.0) {
        vec2 texel = 1.0 / vec2(screenParams.x, screenParams.y);
        float ml = texture(MaskSampler, texCoord + vec2(-texel.x, 0.0)).r;
        float mr = texture(MaskSampler, texCoord + vec2(texel.x, 0.0)).r;
        float mt = texture(MaskSampler, texCoord + vec2(0.0, texel.y)).r;
        float mb = texture(MaskSampler, texCoord + vec2(0.0, -texel.y)).r;
        float edge = clamp((abs(mr - ml) + abs(mt - mb)) * 3.2, 0.0, 1.0);
        cosmos += mix(baseColor, accent, 0.65) * edge * edgeGlow;
    }

    cosmos = clamp(cosmos, 0.0, 1.0);

    if (theme >= 23.5) {
        vec3 sceneAfter = texture(SceneAfterSampler, texCoord).rgb;
        vec3 pulseEffect = clamp(cosmos * max(0.0, pulseAlpha), 0.0, 1.0);

        if (pulseEffectOnly > 0.5) {
            float pulseMix = clamp(mask * min(pulseAlpha, 1.0), 0.0, 1.0);
            fragColor = vec4(mix(scene.rgb, pulseEffect, pulseMix), 1.0);
        } else {
            vec3 withHand = clamp(sceneAfter + pulseEffect, 0.0, 1.0);
            fragColor = vec4(mix(scene.rgb, withHand, mask), 1.0);
        }
        return;
    }

    fragColor = vec4(mix(scene.rgb, cosmos, mask), 1.0);
}
