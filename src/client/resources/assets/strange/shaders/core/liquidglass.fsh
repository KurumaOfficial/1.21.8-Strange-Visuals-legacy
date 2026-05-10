#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec2 CenterUVOut;
in vec4 FragColor;
in vec2 Size;
in vec4 Radius;
in float Smoothness;
in float CornerSmoothness;
in float FresnelPower;
in float DistortStrength;

uniform sampler2D Sampler0;

out vec4 fragColor;

// SDF для скруглённого прямоугольника
float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

// Fresnel reflectance calculation (proper physics)
float fresnel(vec3 I, vec3 N, float ior) {
    float cosi = clamp(-1.0, 1.0, dot(I, N));
    float etai = 1.0, etat = ior;
    if (cosi > 0.0) {
        float temp = etai;
        etai = etat;
        etat = temp;
    }
    float sint = etai / etat * sqrt(max(0.0, 1.0 - cosi * cosi));
    if (sint >= 1.0) {
        return 1.0; // Total internal reflection
    }
    float cost = sqrt(max(0.0, 1.0 - sint * sint));
    cosi = abs(cosi);
    float Rs = ((etat * cosi) - (etai * cost)) / ((etat * cosi) + (etai * cost));
    float Rp = ((etai * cosi) - (etat * cost)) / ((etai * cosi) + (etat * cost));
    return (Rs * Rs + Rp * Rp) / 2.0;
}

void main() {
    vec2 center = Size * 0.5;
    vec2 halfSize = center - 1.0;
    
    // Позиция в пикселях относительно центра
    vec2 pixelPos = FragCoord * Size - center;
    
    // SDF для маски формы
    float dist = sdRoundBox(pixelPos, halfSize, Radius);
    float shapeMask = 1.0 - smoothstep(-Smoothness, Smoothness, dist);
    
    if (shapeMask < 0.001) discard;
    
    // Вычисляем нормаль для fresnel (градиент SDF)
    vec2 eps = vec2(0.5, 0.0);
    float distX = sdRoundBox(pixelPos + eps.xy, halfSize, Radius);
    float distY = sdRoundBox(pixelPos + eps.yx, halfSize, Radius);
    vec2 gradient = vec2(distX - dist, distY - dist);
    vec3 normal = normalize(vec3(gradient, 1.0));
    
    // View direction (looking straight at the glass)
    vec3 viewDir = vec3(0.0, 0.0, -1.0);
    
    // Вычисляем fresnel с правильной физикой
    float ior = 1.5;
    float fresnelAmount = fresnel(viewDir, normal, ior);
    
    // Расстояние от края формы (внутри положительное)
    float innerDist = -dist;
    float maxInnerDist = min(halfSize.x, halfSize.y);
    float normalizedDepth = clamp(innerDist / max(maxInnerDist, 1.0), 0.0, 1.0);
    
    // Edge factor: 1 на краю, 0 в центре (для fresnel и outline)
    float edgeFactor = 1.0 - normalizedDepth;
    
    // ИСКАЖЕНИЕ - упрощенная и ЗАМЕТНАЯ формула
    // Используем направление от центра в пикселях для вычисления искажения
    vec2 dirFromCenter = normalize(pixelPos + vec2(0.0001));
    float distFromCenter = length(pixelPos);
    float maxDist = min(halfSize.x, halfSize.y);
    float normalizedDistPixel = distFromCenter / max(maxDist, 1.0);
    
    // Вычисляем offset в UV пространстве из пикселей
    // Используем умеренный масштаб для избежания артефактов
    float uvPixelScale = 0.02; // Умеренный масштаб для перевода пикселей в UV
    vec2 offsetUV = dirFromCenter * distFromCenter * uvPixelScale;
    float offsetLength = length(offsetUV);
    
    // Edge factor для искажения: 1 на краю, 0 в центре
    float edgeFactorForDistortion = edgeFactor;
    
    // Exponential distortion (как в оригинале) - сильное искажение на краях
    float exponentialDistortion = exp(edgeFactorForDistortion * 6.0) - 1.0;
    
    // Base magnification - должно быть > 1.0 для эффекта линзы (увеличение)
    float baseMagnification = 1.3; // Умеренное увеличение
    float lensStrength = DistortStrength * 20.0; // Умеренное искажение
    float distortionAmount = exponentialDistortion * lensStrength;
    
    // Применяем magnification - сильнее на краях
    float normalizedDistUV = clamp(offsetLength * 3.0, 0.0, 1.0); // Масштаб для UV
    float magnification = baseMagnification + distortionAmount * normalizedDistUV;
    
    // Хроматическая аберрация - умеренная
    float chromaStrength = 0.1;
    float redMagnification = magnification * (1.0 - chromaStrength);
    float greenMagnification = magnification;
    float blueMagnification = magnification * (1.0 + chromaStrength);
    
    // Применяем искажение: center + offset * magnification (как в оригинале)
    vec2 redUV = CenterUVOut + offsetUV * redMagnification;
    vec2 greenUV = CenterUVOut + offsetUV * greenMagnification;
    vec2 blueUV = CenterUVOut + offsetUV * blueMagnification;
    
    // Ограничиваем UV координаты, чтобы избежать артефактов
    redUV = clamp(redUV, vec2(0.0), vec2(1.0));
    greenUV = clamp(greenUV, vec2(0.0), vec2(1.0));
    blueUV = clamp(blueUV, vec2(0.0), vec2(1.0));
    
    // Сэмплируем размытую текстуру с хроматической аберрацией
    vec3 refractedColor = vec3(
        texture(Sampler0, redUV).r,
        texture(Sampler0, greenUV).g,
        texture(Sampler0, blueUV).b
    );
    
    // Применяем цвет стекла
    refractedColor *= FragColor.rgb;
    refractedColor *= vec3(0.95, 0.98, 1.0); // Легкий голубоватый оттенок
    refractedColor += vec3(0.15); // Увеличиваем яркость для видимости
    
    // Fresnel reflection (белое свечение на краях) - более заметное
    vec3 fresnelColor = vec3(1.0);
    vec3 finalColor = mix(refractedColor, fresnelColor, fresnelAmount * 0.4); // Увеличил fresnel
    
    // ПРАВИЛЬНАЯ АЛЬФА - используем альфу из цвета напрямую
    float baseAlpha = FragColor.a;
    float edgeAlpha = min(FragColor.a * 1.2, 1.0); // Немного ярче на краях
    float finalAlpha = mix(baseAlpha, edgeAlpha, edgeFactor * 0.3) * shapeMask; // Плавный переход
    
    if (finalAlpha < 0.001) discard;
    
    // НОРМАЛЬНЫЙ OUTLINE - четкий контур по краям
    float outlineThickness = 0.005;
    float outlineMask = smoothstep(outlineThickness, 0.0, abs(dist));
    
    if (outlineMask > 0.0) {
        // Четкая белая линия по краю
        vec3 outlineColor = vec3(1.0);
        finalColor = mix(finalColor, outlineColor, outlineMask * 0.5); // Более заметный outline
    }
    
    fragColor = ColorModulator * vec4(finalColor, finalAlpha);
}
