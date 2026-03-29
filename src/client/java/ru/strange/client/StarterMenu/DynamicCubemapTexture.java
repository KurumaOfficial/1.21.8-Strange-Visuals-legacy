package ru.strange.client.StarterMenu;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public final class DynamicCubemapTexture extends AbstractTexture {
    private final Identifier id;

    public DynamicCubemapTexture(Identifier id, NativeImage[] faces) {
        this.id = id;
        upload(faces);
    }

    private void upload(NativeImage[] faces) {
        if (faces == null || faces.length != 6) {
            closeFaces(faces);
            throw new IllegalArgumentException("Cubemap requires exactly 6 faces");
        }

        int faceSize = validateFaces(faces);
        NativeImage[] uploadedFaces = null;

        try {
            uploadedFaces = flipFacesVertically(faces);
            this.close();

            GpuDevice device = RenderSystem.getDevice();
            this.glTexture = device.createTexture(
                    () -> id.toString(),
                    GpuTexture.USAGE_CUBEMAP_COMPATIBLE | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    TextureFormat.RGBA8,
                    faceSize,
                    faceSize,
                    6,
                    1
            );
            this.glTextureView = device.createTextureView(this.glTexture);
            this.setFilter(true, false);
            this.setClamp(false);

            CommandEncoder encoder = device.createCommandEncoder();
            for (int layer = 0; layer < 6; layer++) {
                encoder.writeToTexture(this.glTexture, uploadedFaces[layer], 0, layer, 0, 0, faceSize, faceSize, 0, 0);
            }
        } finally {
            closeFaces(uploadedFaces);
            closeFaces(faces);
        }
    }

    private static int validateFaces(NativeImage[] faces) {
        int faceSize = -1;
        for (NativeImage face : faces) {
            if (face == null) {
                throw new IllegalArgumentException("Cubemap contains a null face");
            }

            if (face.getWidth() != face.getHeight()) {
                throw new IllegalArgumentException("Cubemap face must be square");
            }

            if (face.getWidth() <= 0) {
                throw new IllegalArgumentException("Cubemap face size must be positive");
            }

            if (faceSize == -1) {
                faceSize = face.getWidth();
            } else if (face.getWidth() != faceSize) {
                throw new IllegalArgumentException("Cubemap faces must share the same size");
            }
        }
        return faceSize;
    }

    private static NativeImage[] flipFacesVertically(NativeImage[] faces) {
        NativeImage[] flipped = new NativeImage[faces.length];
        boolean success = false;

        try {
            for (int index = 0; index < faces.length; index++) {
                NativeImage source = faces[index];
                NativeImage copy = new NativeImage(source.getFormat(), source.getWidth(), source.getHeight(), false);
                source.copyRect(copy, 0, 0, 0, 0, source.getWidth(), source.getHeight(), false, true);
                flipped[index] = copy;
            }
            success = true;
            return flipped;
        } finally {
            if (!success) {
                closeFaces(flipped);
            }
        }
    }

    private static void closeFaces(NativeImage[] faces) {
        if (faces == null) {
            return;
        }

        for (NativeImage face : faces) {
            if (face != null) {
                face.close();
            }
        }
    }
}
