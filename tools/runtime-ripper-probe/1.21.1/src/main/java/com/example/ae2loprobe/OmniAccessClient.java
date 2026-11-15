package com.example.ae2loprobe;

import com.example.ae2lightoptimizer.client.OmniConnectorScreen;
import com.example.ae2lightoptimizer.omni.OmniConnectorMenu;
import net.minecraft.client.Minecraft;

/** Client half of the omni_access chain: drives the production screen and records native frames. */
public final class OmniAccessClient {
 private static int ticks, phase;
 private static java.util.concurrent.CompletableFuture<Void> reload;
 /** A language or GUI-scale switch reloads resources; capture only after the reload screen is gone. */
 private static void language(Minecraft mc, String locale, int scale) {
  mc.options.guiScale().set(scale); mc.resizeDisplay();
  mc.getLanguageManager().setSelected(locale); mc.options.languageCode = locale;
  reload = mc.reloadResourcePacks();
 }
 public static boolean tick(Minecraft mc) {
  if (!OmniAccessProbe.ready) return false;
  if (ProbeState.finished) return true;
  if (reload != null) {
   if (reload.isDone() && mc.getOverlay() == null) reload = null;
   ticks = 0;
   return false;
  }
  if (++ticks < 35) return false;
  ticks = 0;
  try {
   if (!(mc.screen instanceof OmniConnectorScreen screen)) return false;
   var menu = (OmniConnectorMenu) mc.player.containerMenu;
   switch (phase++) {
    case 0 -> { ClientBootstrap.capture("ae2lo-ripper-omni-access-ui.png"); OmniClient.click(screen, "visibility"); }
    case 1 -> {
     if (!menu.createPrivate) throw new IllegalStateException("Visibility toggle did not reach the server");
     ClientBootstrap.capture("ae2lo-ripper-omni-access-private-toggle.png");
     var field = OmniConnectorScreen.class.getDeclaredField("name"); field.setAccessible(true);
     ((net.minecraft.client.gui.components.EditBox) field.get(screen)).setValue("Client private");
     OmniClient.click(screen, "createLink");
    }
    case 2 -> {
     if (!menu.selectedPrivate) throw new IllegalStateException("Created channel is not private");
     if (!menu.selectedName.equals("Client private")) throw new IllegalStateException("Private create did not synchronize");
     var row = menu.entries().stream().filter(e -> e.name().equals("Client private")).findFirst()
             .orElseThrow(() -> new IllegalStateException("Private channel row missing"));
     if (!row.privateLink()) throw new IllegalStateException("Private row lost its marker");
     OmniAccessProbe.report.put("client_private_row_marker", true);
     ClientBootstrap.capture("ae2lo-ripper-omni-access-private-created.png");
     language(mc, "zh_cn", 2);
    }
    case 3 -> { ClientBootstrap.capture("ae2lo-ripper-omni-access-zh-2x.png"); language(mc, "en_us", 2); }
    case 4 -> {
     ClientBootstrap.capture("ae2lo-ripper-omni-access-en-2x.png");
     mc.player.closeContainer();
     mc.getSingleplayerServer().execute(OmniAccessProbe::finish);
    }
   }
  } catch (Throwable ex) { OmniAccessProbe.fail(ex); return true; }
  return false;
 }
}
