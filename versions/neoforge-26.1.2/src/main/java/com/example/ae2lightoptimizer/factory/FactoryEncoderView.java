package com.example.ae2lightoptimizer.factory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.List;

/** Server-filtered visible positions. The server never trusts this client-facing view for membership. */
public record FactoryEncoderView(List<String> tags, String selected, List<Long> selectedPositions,
                                 List<Long> otherPositions, List<MachineTags> machineTags, String dimension) {
    public record MachineTags(long position, List<String> tags) {
        public static final Codec<MachineTags> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.fieldOf("position").forGetter(MachineTags::position),
            Codec.STRING.listOf().fieldOf("tags").forGetter(MachineTags::tags)
        ).apply(i,MachineTags::new));
        public MachineTags { tags=List.copyOf(tags); }
    }
    public static final FactoryEncoderView EMPTY=new FactoryEncoderView(List.of(),"",List.of(),List.of(),List.<MachineTags>of(),"");
    public static final Codec<FactoryEncoderView> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.STRING.listOf().fieldOf("tags").forGetter(FactoryEncoderView::tags),
        Codec.STRING.fieldOf("selected").forGetter(FactoryEncoderView::selected),
        Codec.LONG.listOf().fieldOf("selected_positions").forGetter(FactoryEncoderView::selectedPositions),
        Codec.LONG.listOf().fieldOf("other_positions").forGetter(FactoryEncoderView::otherPositions),
        MachineTags.CODEC.listOf().optionalFieldOf("machine_tags", List.of()).forGetter(FactoryEncoderView::machineTags),
        Codec.STRING.optionalFieldOf("dimension", "").forGetter(FactoryEncoderView::dimension)
    ).apply(i,FactoryEncoderView::new));
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<FactoryEncoderView>> TYPE=
        FactoryPatternData.COMPONENTS.registerComponentType("encoder_view", b->b.persistent(CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CODEC)));
    public FactoryEncoderView(List<String> tags,String selected,List<Long> selectedPositions,List<Long> otherPositions) { this(tags,selected,selectedPositions,otherPositions, ""); }
    public FactoryEncoderView(List<String> tags,String selected,List<Long> selectedPositions,List<Long> otherPositions,String dimension) { this(tags,selected,selectedPositions,otherPositions,List.of(),dimension); }
    public FactoryEncoderView { tags=List.copyOf(tags);selectedPositions=List.copyOf(selectedPositions);otherPositions=List.copyOf(otherPositions);machineTags=List.copyOf(machineTags); }
}
