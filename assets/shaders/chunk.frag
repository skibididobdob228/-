#version 330 core
in vec2 vUV;
in vec3 vNormal;
in vec3 vLight;     // x=ao*faceShade, y=skyLight(0..1), z=blockLight(0..1)
in vec3 vWorldPos;

out vec4 FragColor;

uniform sampler2D uAtlas;
uniform vec3 uSunDir;       // points toward the sun
uniform float uDayLight;    // 0 (night) .. 1 (noon)
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;
uniform vec3 uCameraPos;
uniform int uAlphaCutout;   // 1 = discard near-transparent texels (leaves/plants)

void main() {
    vec4 tex = texture(uAtlas, vUV);
    if (uAlphaCutout == 1 && tex.a < 0.5) discard;
    if (tex.a < 0.02) discard;

    float ao = vLight.x;

    // Sky light scaled by time of day; block light is constant warm light.
    float skyContribution = vLight.y * mix(0.10, 1.0, uDayLight);
    float blockContribution = vLight.z;

    // Directional sun diffuse term (only meaningful in daylight).
    float diff = max(dot(normalize(vNormal), normalize(uSunDir)), 0.0);
    float sun = diff * uDayLight * 0.35;

    float lightLevel = max(skyContribution + sun, blockContribution);
    lightLevel = clamp(lightLevel, 0.06, 1.0);

    vec3 color = tex.rgb * lightLevel * ao;

    // Warm tint from block light, cool ambient at night.
    color += blockContribution * vec3(0.25, 0.16, 0.05) * tex.rgb;

    // Distance fog.
    float dist = length(vWorldPos - uCameraPos);
    float fog = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    color = mix(color, uFogColor, fog);

    FragColor = vec4(color, tex.a);
}
