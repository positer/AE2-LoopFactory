package com.example.ae2loprobe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import com.example.ae2lightoptimizer.omni.*;
import com.example.ae2lightoptimizer.client.OmniPulseParticle;
public final class OmniParticleAudit {
 public static final BlockPos POS=new BlockPos(12,100,10);
 static net.minecraft.client.particle.Particle make(Minecraft mc,double x,double vx){return mc.particleEngine.createParticle(OmniContent.PULSE.get(),x,100.5,10.5,vx,0,0);}
 public static void online(Minecraft mc){
  OmniProbe.check(OmniContent.BLOCK.get().getTicker(mc.level,mc.level.getBlockState(POS),OmniContent.ENTITY.get())!=null,"client_particle_tick_registered");
  OmniProbe.check(mc.level.getBlockState(POS).getValue(OmniConnectorBlock.ONLINE),"particle_online_state_synchronized");
  var p=make(mc,12.5,.008);OmniProbe.check(p instanceof OmniPulseParticle,"native_white_particle_provider");
  double previous=12.5,maxStep=0;int samples=0;
  while(p.isAlive()&&samples++<60){p.tick();double x=p.getBoundingBox().getCenter().x;if(p.isAlive()){OmniProbe.check(x>=12.06 && x<=12.94,"particle_inside_single_block");maxStep=Math.max(maxStep,x-previous);previous=x;}}
  OmniProbe.check(samples>=40 && samples<=50 && maxStep<=.00801,"particle_slow_outward_lifetime");
  var edge=make(mc,12.935,.008);edge.tick();OmniProbe.check(!edge.isAlive(),"particle_removed_before_leaving_block");
  long before=OmniPulseParticle.created;
  var random=net.minecraft.util.RandomSource.create(17);
  for(int i=0;i<2;i++)OmniContent.BLOCK.get().animateTick(mc.level.getBlockState(POS),mc.level,POS,random);
  OmniProbe.check(OmniPulseParticle.created>before,"production_online_animation_emits");
 }
 public static void offline(Minecraft mc){
  var state=mc.level.getBlockState(POS);OmniProbe.check(!state.getValue(OmniConnectorBlock.ONLINE),"unlinked_particle_state_synchronized");
  long before=OmniPulseParticle.created;var random=net.minecraft.util.RandomSource.create(17);
  for(int i=0;i<48;i++)OmniContent.BLOCK.get().animateTick(state,mc.level,POS,random);
  OmniProbe.check(OmniPulseParticle.created==before,"offline_animation_emits_nothing");
  var p=make(mc,12.5,.008);p.tick();OmniProbe.check(!p.isAlive(),"existing_particle_stops_when_disconnected");
 }
}
