#version 330 core
in vec2 vUv;
out vec4 FragColor;

uniform sampler2D uSource;
uniform vec2 uTexelSize; // 1/width, 1/height
uniform float uRadius;   // radius in pixels (<= 150.0)
uniform float uWeights[49]; // Pre-calculated Gaussian weights

const float MIN_RADIUS = 0.05;
const float MAX_RADIUS = 150.0;
const int MAX_SAMPLES = 48;

void main() {
    float clampedRadius = clamp(uRadius, MIN_RADIUS, MAX_RADIUS);
    vec2 step = vec2(uTexelSize.x, 0.0);
    
    int samples = int(ceil(clampedRadius)) + 1;
    samples = min(samples, MAX_SAMPLES);
    
    vec4 color = texture(uSource, vUv) * uWeights[0];
    
    for (int i = 1; i <= samples; ++i) {
        float weight = uWeights[i];
        float offset = float(i);
        color += (texture(uSource, vUv + step * offset) + texture(uSource, vUv - step * offset)) * weight;
    }

    FragColor = color;
}
