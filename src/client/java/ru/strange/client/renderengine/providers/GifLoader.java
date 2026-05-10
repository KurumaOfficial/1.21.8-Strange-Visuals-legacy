package ru.strange.client.renderengine.providers;

import com.mojang.blaze3d.textures.GpuTextureView;
import me.x150.renderer.util.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.renderengine.renderers.GifRenderer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GifLoader {
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private GifLoader() {
    }

    public static GifData loadGif(File file) throws IOException {
        String key = "file:" + file.getAbsolutePath();
        long lastModified = file.lastModified();
        long fileSize = file.length();
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.data().isClosed() && cached.matches(lastModified, fileSize)) {
            return cached.data();
        }

        if (cached != null) {
            cached.data().close();
            cache.remove(key, cached);
        }

        try (ImageInputStream stream = openImageInputStream(file, file.getAbsolutePath())) {
            GifData data = loadGifFromStream(stream, key);
            cache.put(key, new CacheEntry(data, lastModified, fileSize));
            return data;
        }
    }

    public static GifData loadGif(InputStream inputStream) throws IOException {
        try (ImageInputStream stream = openImageInputStream(inputStream, "stream")) {
            return loadGifFromStream(stream, "stream:" + System.nanoTime());
        }
    }

    public static GifData loadGif(Identifier identifier) throws IOException {
        String key = "identifier:" + identifier;
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.data().isClosed()) {
            return cached.data();
        }
        if (cached != null) {
            cache.remove(key, cached);
        }

        try (InputStream inputStream = MinecraftClient.getInstance().getResourceManager().getResource(identifier).orElseThrow().getInputStream();
             ImageInputStream stream = openImageInputStream(inputStream, identifier.toString())) {
            GifData data = loadGifFromStream(stream, key);
            cache.put(key, new CacheEntry(data, -1L, -1L));
            return data;
        }
    }

    public static void clearCache() {
        for (CacheEntry cacheEntry : cache.values()) {
            cacheEntry.data().close();
        }
        cache.clear();
        GifRenderer.clearAnimationCache();
    }

    private static ImageInputStream openImageInputStream(File file, String sourceLabel) throws IOException {
        ImageInputStream stream = ImageIO.createImageInputStream(file);
        if (stream == null) {
            throw new IOException("Failed to open GIF stream for " + sourceLabel);
        }
        return stream;
    }

    private static ImageInputStream openImageInputStream(InputStream inputStream, String sourceLabel) throws IOException {
        ImageInputStream stream = ImageIO.createImageInputStream(inputStream);
        if (stream == null) {
            throw new IOException("Failed to open GIF stream for " + sourceLabel);
        }
        return stream;
    }

    private static GifData loadGifFromStream(ImageInputStream stream, String cacheKey) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("GIF reader not found");
        }

        ImageReader reader = readers.next();
        reader.setInput(stream);

        List<GpuTextureView> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        List<NativeImageBackedTexture> textures = new ArrayList<>();

        try {
            int numFrames = reader.getNumImages(true);
            int gifWidth = reader.getWidth(0);
            int gifHeight = reader.getHeight(0);
            if (numFrames <= 0 || gifWidth <= 0 || gifHeight <= 0) {
                throw new IOException("GIF contains no renderable frames");
            }
            BufferedImage canvas = new BufferedImage(gifWidth, gifHeight, BufferedImage.TYPE_INT_ARGB);
            BufferedImage savedImage = null;
            FrameMetadata previousFrameMetadata = FrameMetadata.EMPTY;

            for (int i = 0; i < numFrames; i++) {
                applyPreviousDisposal(canvas, savedImage, gifWidth, gifHeight, previousFrameMetadata, i == 0);

                IIOMetadata metadata = readMetadata(reader, cacheKey, i);
                FrameMetadata frameMetadata = parseFrameMetadata(metadata, cacheKey, i);
                BufferedImage frame = toArgb(reader.read(i));

                if ("restoreToPrevious".equals(frameMetadata.disposalMethod())) {
                    savedImage = copyBufferedImage(canvas);
                } else {
                    savedImage = null;
                }

                Graphics2D graphics = canvas.createGraphics();
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.drawImage(frame, frameMetadata.frameX(), frameMetadata.frameY(), null);
                graphics.dispose();

                BufferedImage finalFrame = copyBufferedImage(canvas);
                NativeImageBackedTexture texture = RenderUtils.bufferedImageToNIBT(
                        "gif_frame_" + cacheKey + "_" + i,
                        finalFrame
                );
                textures.add(texture);
                frames.add(texture.getGlTextureView());
                delays.add(readDelayMillis(metadata, cacheKey, i));
                previousFrameMetadata = frameMetadata;
            }

            String gifId = "gif_" + cacheKey.hashCode() + "_" + frames.size();
            if (frames.isEmpty()) {
                throw new IOException("GIF contains no renderable textures");
            }
            return new GifData(frames, delays, textures, gifId);
        } catch (IOException | RuntimeException exception) {
            closeTextures(textures);
            throw exception;
        } finally {
            reader.dispose();
        }
    }

    private static void applyPreviousDisposal(BufferedImage canvas, BufferedImage savedImage, int width, int height, FrameMetadata previousFrameMetadata, boolean firstFrame) {
        Graphics2D graphics = canvas.createGraphics();
        try {
            if (firstFrame) {
                graphics.setComposite(AlphaComposite.Clear);
                graphics.fillRect(0, 0, width, height);
                return;
            }

            if (previousFrameMetadata == null) {
                return;
            }

            if ("restoreToBackgroundColor".equals(previousFrameMetadata.disposalMethod())) {
                graphics.setComposite(AlphaComposite.Clear);
                int clearX = clampFrameBound(previousFrameMetadata.frameX(), width);
                int clearY = clampFrameBound(previousFrameMetadata.frameY(), height);
                int clearWidth = clampFrameExtent(previousFrameMetadata.frameWidth(), clearX, width);
                int clearHeight = clampFrameExtent(previousFrameMetadata.frameHeight(), clearY, height);
                if (clearWidth > 0 && clearHeight > 0) {
                    graphics.fillRect(clearX, clearY, clearWidth, clearHeight);
                }
                return;
            }

            if ("restoreToPrevious".equals(previousFrameMetadata.disposalMethod()) && savedImage != null) {
                graphics.setComposite(AlphaComposite.Src);
                graphics.drawImage(savedImage, 0, 0, null);
            }
        } finally {
            graphics.dispose();
        }
    }

    private static IIOMetadata readMetadata(ImageReader reader, String cacheKey, int frameIndex) {
        try {
            return reader.getImageMetadata(frameIndex);
        } catch (IOException | RuntimeException exception) {
            Strange.LOGGER.debug("Failed to read GIF metadata for {} frame {}", cacheKey, frameIndex, exception);
            return null;
        }
    }

    private static FrameMetadata parseFrameMetadata(IIOMetadata metadata, String cacheKey, int frameIndex) {
        String disposalMethod = "none";
        int frameX = 0;
        int frameY = 0;
        int frameWidth = 0;
        int frameHeight = 0;

        if (metadata == null) {
            return new FrameMetadata(disposalMethod, frameX, frameY, frameWidth, frameHeight);
        }

        try {
            org.w3c.dom.Node tree = metadata.getAsTree("javax_imageio_gif_image_1.0");
            if (tree == null) {
                return new FrameMetadata(disposalMethod, frameX, frameY, frameWidth, frameHeight);
            }

            org.w3c.dom.NodeList children = tree.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node node = children.item(j);
                if ("GraphicControlExtension".equals(node.getNodeName())) {
                    org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
                    if (attrs != null) {
                        org.w3c.dom.Node disp = attrs.getNamedItem("disposalMethod");
                        if (disp != null) {
                            disposalMethod = disp.getNodeValue();
                        }
                    }
                } else if ("ImageDescriptor".equals(node.getNodeName())) {
                    org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
                    if (attrs != null) {
                        org.w3c.dom.Node x = attrs.getNamedItem("imageLeftPosition");
                        org.w3c.dom.Node y = attrs.getNamedItem("imageTopPosition");
                        org.w3c.dom.Node widthNode = attrs.getNamedItem("imageWidth");
                        org.w3c.dom.Node heightNode = attrs.getNamedItem("imageHeight");
                        if (x != null) {
                            frameX = Integer.parseInt(x.getNodeValue());
                        }
                        if (y != null) {
                            frameY = Integer.parseInt(y.getNodeValue());
                        }
                        if (widthNode != null) {
                            frameWidth = Integer.parseInt(widthNode.getNodeValue());
                        }
                        if (heightNode != null) {
                            frameHeight = Integer.parseInt(heightNode.getNodeValue());
                        }
                    }
                }
            }
        } catch (RuntimeException exception) {
            Strange.LOGGER.debug("Failed to parse GIF metadata tree for {} frame {}", cacheKey, frameIndex, exception);
        }

        return new FrameMetadata(disposalMethod, frameX, frameY, frameWidth, frameHeight);
    }

    private static int clampFrameBound(int value, int maxSize) {
        return Math.max(0, Math.min(value, maxSize));
    }

    private static int clampFrameExtent(int size, int start, int maxSize) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(size, maxSize - start));
    }

    private static int readDelayMillis(IIOMetadata metadata, String cacheKey, int frameIndex) {
        int delay = 100;
        if (metadata == null) {
            return delay;
        }

        try {
            String[] formats = metadata.getMetadataFormatNames();
            for (String format : formats) {
                try {
                    org.w3c.dom.Node tree = metadata.getAsTree(format);
                    org.w3c.dom.NodeList nodes = tree.getChildNodes();
                    for (int j = 0; j < nodes.getLength(); j++) {
                        org.w3c.dom.Node node = nodes.item(j);
                        if (!"GraphicControlExtension".equals(node.getNodeName())) {
                            continue;
                        }

                        org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
                        if (attrs == null) {
                            continue;
                        }

                        org.w3c.dom.Node delayNode = attrs.getNamedItem("delayTime");
                        if (delayNode == null) {
                            continue;
                        }

                        delay = Integer.parseInt(delayNode.getNodeValue()) * 10;
                        return Math.max(16, delay);
                    }
                } catch (RuntimeException exception) {
                    Strange.LOGGER.debug("Failed to parse GIF delay metadata format {} for {} frame {}", format, cacheKey, frameIndex, exception);
                }
            }
        } catch (RuntimeException exception) {
            Strange.LOGGER.debug("Failed to read GIF delay metadata for {} frame {}", cacheKey, frameIndex, exception);
        }

        return Math.max(16, delay);
    }

    private static BufferedImage toArgb(BufferedImage frame) {
        if (frame.getType() == BufferedImage.TYPE_INT_ARGB) {
            return frame;
        }

        BufferedImage converted = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        graphics.drawImage(frame, 0, 0, null);
        graphics.dispose();
        return converted;
    }

    private static BufferedImage copyBufferedImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static void closeTextures(List<NativeImageBackedTexture> textures) {
        for (NativeImageBackedTexture texture : textures) {
            try {
                texture.close();
            } catch (RuntimeException exception) {
                Strange.LOGGER.debug("Failed to close GIF texture", exception);
            }
        }
    }

    private record CacheEntry(GifData data, long lastModified, long fileSize) {
        private boolean matches(long lastModified, long fileSize) {
            return this.lastModified == lastModified && this.fileSize == fileSize;
        }
    }

    private record FrameMetadata(String disposalMethod, int frameX, int frameY, int frameWidth, int frameHeight) {
        private static final FrameMetadata EMPTY = new FrameMetadata("none", 0, 0, 0, 0);
    }

    public static class GifData implements AutoCloseable {
        private final List<GpuTextureView> frames;
        private final List<Integer> delays;
        private final List<NativeImageBackedTexture> textures;
        private final String gifId;
        private boolean closed;

        public GifData(List<GpuTextureView> frames, List<Integer> delays, List<NativeImageBackedTexture> textures, String gifId) {
            this.frames = List.copyOf(frames);
            this.delays = List.copyOf(delays);
            this.textures = List.copyOf(textures);
            this.gifId = gifId;
        }

        public List<GpuTextureView> getFrames() {
            return frames;
        }

        public List<Integer> getDelays() {
            return delays;
        }

        public String getGifId() {
            return gifId;
        }

        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }

            closed = true;
            GifRenderer.resetAnimation(gifId);
            closeTextures(textures);
        }
    }
}
