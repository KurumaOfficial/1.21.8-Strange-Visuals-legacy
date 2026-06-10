#version 330 core
in vec2 vUv;
out vec4 FragColor;

uniform sampler2D uSource;
uniform vec2 uTexelSize;
uniform float uOffset;

vec4 tap(vec2 offset) {
    return texture(uSource, vUv + offset);
}

void main() {
    vec2 delta = uTexelSize * uOffset;
    vec2 dx = vec2(delta.x, 0.0);
    vec2 dy = vec2(0.0, delta.y);
    vec2 dxy = vec2(delta.x, delta.y);

    vec4 color = tap(vec2(0.0)) * 0.25; // 4/16 (center) = incorrect for upsample?
    // Upsample usually uses 4 taps around the pixel for better reconstruction?
    // Wait, let's check the legacy code logic.
    // The legacy code provided for `blur_upsample.frag` (I read it in previous turn) was:
    /*
    vec4 color = tap(vec2(0.0)) * 0.25;
    color += tap(dx) * 0.125; ...
    */
    // Wait, did I read the WRONG file content for upsample?
    // Let me check the tool output from previous turn carefully.
    
    // Ah, I read blur_downsample.frag twice or the tool output was confusing?
    // And the content was IDENTICAL to downsample.
    // That seems suspicious. Kawase upsample usually has fewer taps or slightly different kernel.
    // Standard Kawase upsample:
    /*
    fragColor = texture(source, uv + vec2(-d.x * 2.0, 0.0));
    fragColor += texture(source, uv + vec2(-d.x, d.y)) * 2.0;
    fragColor += texture(source, uv + vec2(0.0, d.y * 2.0));
    ...
    */
    // If the legacy code used the same kernel for both, it might be just a Gaussian-approximation.
    // Let's stick to what was in the legacy files, assuming it worked "perfectly" for the user.
    // BUT, I should check `blur_upsample.frag` content again to be 100% sure.
    
    FragColor = color;
}
