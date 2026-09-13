package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Reproduces the user's saved tag overlap and obsolete program without changing their world. */
final class SavedSceneMisrouteAudit {
    private static boolean done;
    static boolean tick(FactoryBlockEntity host,BlockPos src,BlockPos furnace,BlockPos out) throws Exception {
        if(done)return true;
        var level=(ServerLevel)host.getLevel();
        level.setBlockAndUpdate(src,Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(furnace,Blocks.FURNACE.defaultBlockState());
        level.setBlockAndUpdate(out,Blocks.CHEST.defaultBlockState());
        var a=(Container)level.getBlockEntity(src);var f=(Container)level.getBlockEntity(furnace);var b=(Container)level.getBlockEntity(out);
        var rows=new ArrayList<Map<String,Object>>();
        String current="name \"Fue\"\nimport Furnance\nimport stor,out\nwhile true do\n    channel\n        get * from stor\n        put * into Furnance on up\n        put * into Furnance on west\n    channel\n        get * from Furnance on down\n        put * into out\n    wait 1 tick\ndone";
        String previous="name \"Fue\"\nimport Furnance\nimport stor,out\nwhile true do\n    get * from stor\n    put * into Furnance on up\n    put * into Furnance on west\n    get * from Furnance on down\n    put * into out\n    wait 1 tick\ndone";
        reset(a,f,b);bind(host,src,furnace,out,true);
        var job=run(host,current);
        require(count(a,Items.OAK_LOG)==0&&count(b,Items.OAK_LOG)==64,"overlap sends raw input straight into output chest");
        rows.add(row("current-code-overlapping-tags",a,f,b,job));
        reset(a,f,b);bind(host,src,furnace,out,false);
        job=run(host,current);
        require(count(a,Items.OAK_LOG)==64&&count(b,Items.OAK_LOG)==0,"distinct tags keep raw material out of output channel");
        rows.add(row("current-code-distinct-tags",a,f,b,job));
        // A concurrently retained pre-channel job can still move raw stock to out.
        job=run(host,previous);
        require(count(a,Items.OAK_LOG)==0&&count(b,Items.OAK_LOG)==64,"obsolete non-channel job reproduces independent misdelivery");
        rows.add(row("retained-old-code-distinct-tags",a,f,b,job));
        a.clearContent();f.clearContent();b.clearContent();
        a.setItem(0,new ItemStack(Items.OAK_LOG,4));a.setItem(1,new ItemStack(Items.COAL,3));
        job=run(host,"import stor,Furnance,out\nchannel\n    get 4 minecraft:oak_log from stor\n    put 4 minecraft:oak_log into Furnance on up\nchannel\n    get 3 minecraft:coal from stor\n    put 3 minecraft:coal into Furnance on west\ndone");
        require(f.getItem(0).is(Items.OAK_LOG)&&f.getItem(0).getCount()==4&&f.getItem(1).is(Items.COAL)&&f.getItem(1).getCount()==3,"UP targets furnace input and WEST targets fuel");
        rows.add(row("explicit-input-and-fuel-faces",a,f,b,job));
        f.setItem(2,new ItemStack(Items.CHARCOAL,2));
        job=run(host,"import Furnance,out\nchannel\n    get * from Furnance on down\n    put * into out\ndone");
        require(count(b,Items.CHARCOAL)==2&&f.getItem(0).getCount()==4&&f.getItem(1).getCount()==3,"DOWN exports result without extracting input or coal fuel");
        rows.add(row("explicit-output-face",a,f,b,job));
        a.setItem(0,new ItemStack(Items.OAK_LOG,5));
        job=run(host,"import stor,Furnance\nget * from stor\nput * into Furnance on down\ndone");
        require(count(a,Items.OAK_LOG)==5&&f.getItem(0).getCount()==4,"wrong insertion face never falls back to UP");
        rows.add(row("wrong-face-no-fallback",a,f,b,job));
        Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("saved-scene-misroute-report.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status","passed","cases",rows,"scope","Separate native fixture; user's save is read-only")));
        done=true;return true;
    }
    private static FactoryJob run(FactoryBlockEntity host,String code) {
        var job=new FactoryJob(host,new FactoryPatternData(code,host.factoryId(),ItemStack.EMPTY));job.tick();
        require(job.error().isEmpty(),job.error());return job;
    }
    private static void reset(Container a,Container f,Container b) {
        a.clearContent();f.clearContent();b.clearContent();
        a.setItem(0,new ItemStack(Items.OAK_LOG,64));f.setItem(0,new ItemStack(Items.COBBLESTONE,64));f.setItem(1,new ItemStack(Items.COAL,64));
    }
    private static void bind(FactoryBlockEntity host,BlockPos a,BlockPos f,BlockPos b,boolean overlap) {
        host.tags=new FactoryTags();host.tags.reconcile(List.of("stor","Furnance","out"));
        require(host.tags.tag("stor",a.asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"source member");
        require(host.tags.tag("Furnance",f.asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"furnace member");
        require(host.tags.tag("out",b.asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"out member");
        if(overlap)host.tags.tag("Furnance",b.asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p)));
    }
    private static int count(Container c,Item item) {int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
    private static Map<String,Object> row(String name,Container a,Container f,Container b,FactoryJob job) {
        return Map.of("id",name,"passed",true,"sourceLogs",count(a,Items.OAK_LOG),"outputLogs",count(b,Items.OAK_LOG),
                "outputCharcoal",count(b,Items.CHARCOAL),"furnaceSlots",List.of(f.getItem(0).toString(),f.getItem(1).toString(),f.getItem(2).toString()),"routes",job.save().routes(),"continuation",job.save().continuation());
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
}
