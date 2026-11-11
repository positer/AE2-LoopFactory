package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.network.OmniWhitePalette;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Runtime palette adapter: preserves the original UV grid and alpha, including resource-pack overrides. */
public final class OmniWhiteSprite implements SpriteSource {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("ae2lightoptimizer", "block/omni_white");
    public static final MapCodec<OmniWhiteSprite> CODEC = MapCodec.unit(OmniWhiteSprite::new);
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);
    public static volatile int sourceWidth, sourceHeight, changedPixels, coloredPixelsRemaining;
    public static void register(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("ae2lightoptimizer", "omni_white"), TYPE);
    }
    @Override public SpriteSourceType type() { return TYPE; }
    @Override public void run(ResourceManager manager, Output output) {
        var resource = manager.getResource(ResourceLocation.fromNamespaceAndPath("ae2", "textures/block/wireless_access_point.png")).orElseThrow();
        output.add(ID, loader -> {
            try (var input = resource.open()) {
                var image = NativeImage.read(input);
                sourceWidth = image.getWidth(); sourceHeight = image.getHeight(); int changed = 0, colored = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int before = image.getPixelRGBA(x, y), after = OmniWhitePalette.neutralize(before);
                    if ((after >>> 24) != 0 && ((after & 255) != ((after >>> 8) & 255) || (after & 255) != ((after >>> 16) & 255))) colored++;
                    if (before != after) { image.setPixelRGBA(x, y, after); changed++; }
                }
                changedPixels = changed; coloredPixelsRemaining = colored;
                return new SpriteContents(ID, new FrameSize(image.getWidth(), image.getHeight()), image, resource.metadata());
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load native AE2 wireless texture", error); }
        });
    }
}
