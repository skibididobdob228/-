#version 330 core
in vec3 vNormal;
in vec3 vWorld;
out vec4 FragColor;
uniform vec3 uColor;
uniform vec3 uSunDir;
uniform float uDayLight;
uniform float uAmbient;
uniform vec3 uFogColor;
uniform float uFogStart, uFogEnd;
uniform vec3 uCameraPos;
void main() {
    float diff = max(dot(normalize(vNormal), normalize(uSunDir)), 0.0);
    float light = max(uAmbient, 0.35 + diff * 0.5 * uDayLight);
    vec3 color = uColor * clamp(light, 0.0, 1.0);
    float dist = length(vWorld - uCameraPos);
    float fog = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    FragColor = vec4(mix(color, uFogColor, fog), 1.0);
}
