package com.example.ae2loprobe;
import net.minecraft.client.Minecraft;
import com.example.ae2lightoptimizer.client.OmniConnectorScreen;
import com.example.ae2lightoptimizer.omni.OmniConnectorMenu;
public final class OmniClient {
 static void click(OmniConnectorScreen screen,String id)throws Exception{
 var f=appeng.client.gui.AEBaseScreen.class.getDeclaredField("widgets");f.setAccessible(true);
 var b=((com.example.ae2lightoptimizer.mixin.WidgetContainerAccessor)f.get(screen)).ae2lightoptimizer$getWidgets().get(id);
 if(b==null || !b.active)throw new IllegalStateException("Missing/disabled widget "+id);
 screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(b.getX()+8,b.getY()+8,new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
 }
 static java.util.concurrent.CompletableFuture<Void> reload;
 static void language(Minecraft mc,String locale,int scale){mc.options.guiScale().set(scale);mc.resizeGui();mc.getLanguageManager().setSelected(locale);mc.options.languageCode=locale;reload=mc.reloadResourcePacks();}
 static int ticks,phase=-1;
 public static boolean tick(Minecraft mc){
 if(!OmniProbe.ready)return false;
 if(ProbeState.finished)return true;
 if(reload!=null){if(reload.isDone() && mc.getOverlay()==null)reload=null;ticks=0;return false;}
 if(++ticks<35)return false;ticks=0;
 try{
 if(phase==8){ClientBootstrap.capture("ae2lo-ripper-omni-world.png");phase=9;return false;}
 if(phase==9){OmniParticleAudit.online(mc);phase=10;return false;}
 if(phase==10){ClientBootstrap.capture("ae2lo-ripper-omni-particles-online.png");mc.getSingleplayerServer().execute(()->((com.example.ae2lightoptimizer.omni.OmniConnectorBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(OmniParticleAudit.POS)).selectLink(""));phase=11;return false;}
 if(phase==11){OmniParticleAudit.offline(mc);ClientBootstrap.capture("ae2lo-ripper-omni-particles-offline.png");mc.getSingleplayerServer().execute(()->((com.example.ae2lightoptimizer.omni.OmniConnectorBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(OmniParticleAudit.POS)).selectLink(OmniProbe.link));phase=12;return false;}
 if(phase==12){if(!mc.level.getBlockState(OmniParticleAudit.POS).getValue(com.example.ae2lightoptimizer.omni.OmniConnectorBlock.ONLINE))throw new IllegalStateException("Particle reconnect online state missing");mc.getSingleplayerServer().execute(OmniProbe::finish);return false;}
 if(!(mc.screen instanceof OmniConnectorScreen screen))return false;
 var menu=(OmniConnectorMenu)mc.player.containerMenu;
 switch(phase++){
 case -1->{ClientBootstrap.capture("ae2lo-ripper-omni-ui.png");click(screen,"createLink");}
 case 0->{if(!menu.invalidName)throw new IllegalStateException("Empty name accepted");ClientBootstrap.capture("ae2lo-ripper-omni-invalid.png");var f=OmniConnectorScreen.class.getDeclaredField("name");f.setAccessible(true);((net.minecraft.client.gui.components.EditBox)f.get(screen)).setValue("Client created link");click(screen,"createLink");}
 case 1->{if(!menu.selectedName.equals("Client created link"))throw new IllegalStateException("C2S create did not synchronize");OmniProbe.report.put("native_client_create_sync",true);ClientBootstrap.capture("ae2lo-ripper-omni-created.png");click(screen,"next");}
 case 2->{if(menu.page!=1)throw new IllegalStateException("Page synchronization");ClientBootstrap.capture("ae2lo-ripper-omni-page2.png");click(screen,"unlink");}
 case 3->{if(!menu.selectedId.isEmpty())throw new IllegalStateException("Unlink synchronization");ClientBootstrap.capture("ae2lo-ripper-omni-unlinked.png");menu.select(OmniProbe.link);}
 case 4->{if(!menu.selectedId.equals(OmniProbe.link))throw new IllegalStateException("Select synchronization");ClientBootstrap.capture("ae2lo-ripper-omni-restored.png");language(mc,"zh_cn",1);}
 case 5->{ClientBootstrap.capture("ae2lo-ripper-omni-zh-1x.png");language(mc,"zh_cn",2);}
 case 6->{ClientBootstrap.capture("ae2lo-ripper-omni-zh-2x.png");language(mc,"en_us",2);}
 case 7->{ClientBootstrap.capture("ae2lo-ripper-omni-en-2x.png");mc.player.closeContainer();mc.getSingleplayerServer().execute(()->{
 var level=mc.getSingleplayerServer().overworld();var facing=net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
 level.setBlockAndUpdate(new net.minecraft.core.BlockPos(10,100,10),appeng.core.definitions.AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState().setValue(facing,net.minecraft.core.Direction.SOUTH));
 OmniProbe.battery(new net.minecraft.core.BlockPos(10,100,9));
 var pos=new net.minecraft.core.BlockPos(12,100,10);
 level.setBlockAndUpdate(pos,com.example.ae2lightoptimizer.omni.OmniContent.BLOCK.get().defaultBlockState().setValue(facing,net.minecraft.core.Direction.SOUTH));
 ((com.example.ae2lightoptimizer.omni.OmniConnectorBlockEntity)level.getBlockEntity(pos)).selectLink(OmniProbe.link);
 OmniProbe.player.connection.teleport(11.5,99.8,13,180,25);
 });}

 }

 }catch(Throwable ex){OmniProbe.fail(ex);return true;}
 return false;
 }
}
