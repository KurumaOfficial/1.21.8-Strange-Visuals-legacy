#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 FragCoord;
in vec4 FragColor;
in vec2 Size;
in vec4 Radius;
in float Smoothness;
in float BlurRadius;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

float sdBox(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

void main() {
    // Size содержит расширенный размер (оригинальный + BlurRadius * 2)
    // Вычисляем оригинальный размер
    vec2 originalSize = Size - vec2(BlurRadius * 2.0);
    // Центр расширенной области (совпадает с центром оригинальной формы)
    vec2 center = Size * 0.5;
    // Полуразмер оригинальной формы (для границ)
    vec2 originalHalfSize = originalSize * 0.5;
    
    // Позиция в расширенной области
    vec2 pos = FragCoord * Size;
    // Координаты относительно центра (как в rectangle.fsh)
    vec2 localPos = center - pos;
    
    float shapeDist;
    
    // Определяем форму: если радиусы все нулевые, используем прямоугольник, иначе скругленный прямоугольник
    // Используем originalHalfSize для границ формы (как в rectangle.fsh используется center - 1.0)
    if (Radius.x == 0.0 && Radius.y == 0.0 && Radius.z == 0.0 && Radius.w == 0.0) {
        shapeDist = sdBox(localPos, originalHalfSize - 1.0);
    } else {
        shapeDist = sdRoundBox(localPos, originalHalfSize - 1.0, Radius);
    }
    
    // Применяем размытие через smoothstep для создания мягкой тени
    // Тень работает как border - внутри прозрачно, снаружи размытие
    float blurRange = max(BlurRadius, 1.0);
    float alpha = 0.0;
    
    // Используем равномерное размытие на основе расстояния от края формы
    if (shapeDist <= 0.0) {
        // Внутри формы - полностью прозрачно (как border)
        alpha = 0.0;
    } else {
        // Вне формы - применяем плавное затухание для создания мягкой тени
        // Используем расстояние от края формы для размытия
        float dist = max(0.0, shapeDist);
        // Создаем плавное затухание от 1.0 до 0.0 на расстоянии blurRange
        alpha = 1.0 - smoothstep(0.0, blurRange, dist);
    }
    
    // Применяем smoothness для более плавных краев на границе формы
    if (Smoothness > 0.0) {
        // Плавный переход на границе формы (от 0 внутри до размытия снаружи)
        float edgeAlpha = 1.0 - smoothstep(-Smoothness, Smoothness, shapeDist);
        // Комбинируем с размытием
        if (shapeDist > 0.0) {
            alpha = max(alpha, edgeAlpha * 0.5);
        }
    }
    
    // Умножаем на альфу цвета
    alpha *= FragColor.a;
    
    vec4 color = ColorModulator * vec4(FragColor.rgb, alpha);
    if (color.a <= 0.0) discard;

    fragColor = color;
}
