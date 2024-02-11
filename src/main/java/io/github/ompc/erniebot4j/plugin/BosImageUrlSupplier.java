package io.github.ompc.erniebot4j.plugin;

import io.github.ompc.erniebot4j.bce.bos.BosClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * 使用百度BOS服务获取图片URL。
 * <p>
 * 在当前版本中，智慧图问的要求必须是百度BOS的图片URL地址，因此需要将图片上传到百度BOS服务。
 * </p>
 */
public class BosImageUrlSupplier implements ImageUrlSupplier {

    private final BosClient bos;
    private final String bucket;
    private final Supplier<CompletableFuture<BufferedImage>> supplier;

    private BosImageUrlSupplier(Builder builder) {
        this.bos = requireNonNull(builder.bos);
        this.bucket = requireNonNull(builder.bucket);
        this.supplier = requireNonNull(builder.supplier);
    }

    @Override
    public CompletableFuture<URL> get() {
        return supplier.get()
                .thenCompose(image -> {
                    try (final var output = new ByteArrayOutputStream()) {
                        ImageIO.write(image, "png", output);
                        final var data = output.toByteArray();
                        final var name = String.format("%s.png", UUID.randomUUID());
                        return bos.put(bucket, name, data);
                    } catch (IOException ex) {
                        return failedFuture(ex);
                    }
                });
    }

    public static class Builder {

        private BosClient bos;
        private String bucket;
        private Supplier<CompletableFuture<BufferedImage>> supplier;

        public Builder bos(BosClient bos) {
            this.bos = bos;
            return this;
        }

        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder image(BufferedImage image) {
            this.supplier = () -> completedFuture(image);
            return this;
        }

        public Builder image(File file) {
            this.supplier = () -> {
                try {
                    return completedFuture(ImageIO.read(file));
                } catch (IOException ex) {
                    return failedFuture(ex);
                }
            };
            return this;
        }

        public Builder image(InputStream input) {
            this.supplier = () -> {
                try (input) {
                    return completedFuture(ImageIO.read(input));
                } catch (IOException ex) {
                    return failedFuture(ex);
                }
            };
            return this;
        }

        public BosImageUrlSupplier build() {
            return new BosImageUrlSupplier(this);
        }

    }

}
