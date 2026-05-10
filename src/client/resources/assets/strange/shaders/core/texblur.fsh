#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;
in vec2 Size;
in vec4 Radius;
in float Smoothness;
in float BlurRadius;

uniform sampler2D Sampler0; // С рукой
uniform sampler2D Sampler1; // БЕЗ руки

out vec4 fragColor;

const float TAU = 6.28318530718;
const int SAMPLES = 24;

// Функция для скругления (твоя)
float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

// Защита от выхода за границы (Фикс полос на небе)
vec4 safeTexture(sampler2D s, vec2 uv) {
    return texture(s, clamp(uv, 0.002, 0.998));
}

void main() {
    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);
    vec2 blurSize = texelSize * BlurRadius;

    vec4 withHand = texture(Sampler0, TexCoord);
    vec4 withoutHand = texture(Sampler1, TexCoord);

    // --- ЛОГИКА МАСКИ ---
    // Так как мы захватываем кадры с идеальным таймингом,
    // разница между кадрами - это ТОЛЬКО рука.
    // Мы можем использовать RGB разницу.
    vec3 colorDiffVector = abs(withHand.rgb - withoutHand.rgb);
    float diff = max(colorDiffVector.r, max(colorDiffVector.g, colorDiffVector.b));

    // Добавляем проверку альфы на всякий случай
    float alphaDiff = abs(withHand.a - withoutHand.a);
    float totalDiff = max(diff, alphaDiff);

    // Порог может быть супер-низким (1/255 ~= 0.0039).
    // Ставим чуть ниже, чтобы ловить даже малейшие изменения теней на руке.
    // Это решит проблему "дырок" в предмете.
    if (totalDiff > 0.001) {

        // --- РАЗМЫТИЕ ---
        vec3 blurredBg = vec3(0.0);
        float totalWeight = 0.0;
        float angleStep = TAU / float(SAMPLES);

        for (float angle = 0.0; angle < TAU; angle += angleStep) {
            // Оптимизированный цикл (3 шага дистанции достаточно для гладкости)
            for (float dist = 0.3; dist <= 1.0; dist += 0.3) {
                vec2 offset = vec2(cos(angle), sin(angle)) * blurSize * dist;

                // Блюрим фон (Sampler1), чтобы получить эффект стекла
                blurredBg += safeTexture(Sampler1, TexCoord + offset).rgb;
                totalWeight += 1.0;
            }
        }
        blurredBg /= totalWeight;

        // Рисуем размытый фон.
        // mix позволяет регулировать прозрачность "стекла".
        // 0.95 * mask делает края мягкими.
        vec3 result = mix(withoutHand.rgb, blurredBg, 1.0); // 1.0 = полное стекло

        // Применяем скругление границ экрана (если нужно)
        vec2 center = Size * 0.5;
        vec2 pos = FragCoord * Size;
        float shapeDist = sdRoundBox(center - pos, center - 1.0, Radius);
        float shapeAlpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, shapeDist);

        vec4 finalColor = vec4(result, 1.0) * FragColor;
        finalColor.a *= shapeAlpha;

        fragColor = finalColor;
    } else {
        // Если это не рука - рисуем прозрачность.
        // Это важно, чтобы не перекрывать GUI и HUD игры.
        fragColor = vec4(0.0);
    }
}