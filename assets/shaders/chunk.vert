#version 330 core
layout(location=0) in vec3 aPos;
layout(location=1) in vec2 aUV;
layout(location=2) in vec3 aNormal;
layout(location=3) in vec3 aLight; // x=ao*faceShade, y=skyLight, z=blockLight

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProj;

out vec2 vUV;
out vec3 vNormal;
out vec3 vLight;
out vec3 vWorldPos;

void main() {
    vec4 world = uModel * vec4(aPos, 1.0);
    vWorldPos = world.xyz;
    vUV = aUV;
    vNormal = aNormal;
    vLight = aLight;
    gl_Position = uProj * uView * world;
}
