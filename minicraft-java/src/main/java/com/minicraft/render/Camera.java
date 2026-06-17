package com.minicraft.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** View/projection provider plus a frustum for chunk culling. */
public class Camera {
    public final Vector3f position = new Vector3f(0, 80, 0);
    public float yaw = -90f, pitch = 0f;
    public float fov = 70f, aspect = 16f / 9f;
    public float nearPlane = 0.05f, farPlane = 1000f;

    private final Vector4f[] planes = new Vector4f[6];

    public Camera() {
        for (int i = 0; i < 6; i++) planes[i] = new Vector4f();
    }

    public Vector3f front() {
        double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw));
        double cp = Math.cos(Math.toRadians(pitch)), sp = Math.sin(Math.toRadians(pitch));
        return new Vector3f((float)(cy * cp), (float) sp, (float)(sy * cp)).normalize();
    }

    public Vector3f forwardXZ() {
        double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw));
        return new Vector3f((float) cy, 0, (float) sy).normalize();
    }

    public void addMouseDelta(float dx, float dy, float sensitivity) {
        yaw += dx * sensitivity;
        pitch -= dy * sensitivity;
        if (pitch > 89.5f) pitch = 89.5f;
        if (pitch < -89.5f) pitch = -89.5f;
    }

    public Matrix4f view() {
        Vector3f f = front();
        Vector3f center = new Vector3f(position).add(f);
        return new Matrix4f().lookAt(position, center, new Vector3f(0, 1, 0));
    }

    public Matrix4f projection() {
        return new Matrix4f().perspective((float) Math.toRadians(fov), aspect, nearPlane, farPlane);
    }

    public void updateFrustum() {
        Matrix4f m = projection().mul(view());
        // Gribb/Hartmann extraction.
        set(0, m.m03() + m.m00(), m.m13() + m.m10(), m.m23() + m.m20(), m.m33() + m.m30());
        set(1, m.m03() - m.m00(), m.m13() - m.m10(), m.m23() - m.m20(), m.m33() - m.m30());
        set(2, m.m03() + m.m01(), m.m13() + m.m11(), m.m23() + m.m21(), m.m33() + m.m31());
        set(3, m.m03() - m.m01(), m.m13() - m.m11(), m.m23() - m.m21(), m.m33() - m.m31());
        set(4, m.m03() + m.m02(), m.m13() + m.m12(), m.m23() + m.m22(), m.m33() + m.m32());
        set(5, m.m03() - m.m02(), m.m13() - m.m12(), m.m23() - m.m22(), m.m33() - m.m32());
        for (Vector4f p : planes) {
            float len = (float) Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z);
            if (len > 0) { p.x /= len; p.y /= len; p.z /= len; p.w /= len; }
        }
    }

    private void set(int i, float a, float b, float c, float d) { planes[i].set(a, b, c, d); }

    public boolean aabbVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (Vector4f p : planes) {
            float px = p.x >= 0 ? maxX : minX;
            float py = p.y >= 0 ? maxY : minY;
            float pz = p.z >= 0 ? maxZ : minZ;
            if (p.x * px + p.y * py + p.z * pz + p.w < 0) return false;
        }
        return true;
    }
}
