package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.factory.FactoryEditorMenu;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Explicit server-provided provider choices; selecting a row sends only the stable UUID. */
public final class FactoryUploadScreen extends Screen {
    private final Screen parent;
    private final FactoryEditorMenu menu;
    private int page;
    private String observedProviders="";
    private int observedPage=-1;
    public FactoryUploadScreen(Screen parent, FactoryEditorMenu menu) {
        super(Component.translatable("gui.ae2lightoptimizer.factory.upload"));this.parent=parent;this.menu=menu;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick() {
        super.tick();
        if(!observedProviders.equals(menu.providers)||observedPage!=menu.providerPage){page=0;rebuildWidgets();}
    }
    @Override protected void init() {
        observedProviders=menu.providers;observedPage=menu.providerPage;
        String[] rows=menu.providers.isEmpty()?new String[0]:menu.providers.split("\n");
        int capacity=Math.max(1,(height-80)/24);
        for(int i=page*capacity;i<Math.min(rows.length,(page+1)*capacity);i++) {
            String[] row=rows[i].split("[|]",4);
            Component label=row.length==4?Component.translatable("gui.ae2lightoptimizer.factory.provider_choice",row[1],row[2],Component.translatable("gui.ae2lightoptimizer.factory.direction."+row[3])):Component.literal(row[0]);
            addRenderableWidget(Button.builder(label, button->{menu.upload(row[0]);minecraft.setScreen(parent);})
                .bounds(width/2-140,30+(i%capacity)*24,280,20).build());
        }
        if(rows.length==0) {
            var empty=Button.builder(Component.translatable("gui.ae2lightoptimizer.factory.no_providers"),b->{}).bounds(width/2-140,30,280,20).build();
            empty.active=false;addRenderableWidget(empty);
        }
        if(rows.length>capacity||menu.providerPages>1)addRenderableWidget(Button.builder(Component.translatable("gui.ae2lightoptimizer.factory.next"),b->{if((page+1)*capacity<rows.length){page++;rebuildWidgets();}else{page=0;menu.nextProviders();if(menu.providerPages==1)rebuildWidgets();}}).bounds(width/2-140,height-46,136,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2lightoptimizer.factory.back"),b->minecraft.setScreen(parent)).bounds(width/2+4,height-46,136,20).build());
    }
    @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float delta) {
        g.fill(width/2-150,4,width/2+150,height-18,0xffc6c6d0);
        g.drawCenteredString(font,title,width/2,12,0xff303038);
        super.render(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
