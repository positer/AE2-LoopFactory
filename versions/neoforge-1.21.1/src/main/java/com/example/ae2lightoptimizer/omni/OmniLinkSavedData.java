package com.example.ae2lightoptimizer.omni;

import com.example.ae2lightoptimizer.network.OmniLinkDirectory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class OmniLinkSavedData extends SavedData {
    private static final Codec<OmniLinkDirectory.Link> LINK = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(OmniLinkDirectory.Link::id),
            Codec.STRING.fieldOf("name").forGetter(OmniLinkDirectory.Link::name)).apply(i, OmniLinkDirectory.Link::new));
    public static final Codec<OmniLinkSavedData> CODEC = LINK.listOf().fieldOf("links").xmap(
            links -> new OmniLinkSavedData(new OmniLinkDirectory(links)), data -> data.directory.links()).codec();
    private final OmniLinkDirectory directory;
    public OmniLinkSavedData() { this(new OmniLinkDirectory()); }
    private OmniLinkSavedData(OmniLinkDirectory directory) { this.directory = directory; }
    public OmniLinkDirectory directory() { return directory; }
    public OmniLinkDirectory.Link create(String name) {
        int previous = directory.size();
        var link = directory.createOrGet(name);
        if (directory.size() != previous) setDirty();
        return link;
    }
    public static OmniLinkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(OmniLinkSavedData::new,
                (tag, registries) -> CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow()), "ae2lf_omni_links");
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return tag.merge((CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow());
    }
}
