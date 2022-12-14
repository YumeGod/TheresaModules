package cn.loli.client.module.modules.combat;

import cn.loli.client.events.PacketEvent;
import cn.loli.client.events.RenderEvent;
import cn.loli.client.events.UpdateEvent;
import cn.loli.client.injection.implementations.IS27PacketExplosion;
import cn.loli.client.module.Module;
import cn.loli.client.module.ModuleCategory;
import cn.loli.client.utils.misc.ChatUtils;


import dev.xix.event.bus.IEventListener;
import dev.xix.property.impl.BooleanProperty;
import dev.xix.property.impl.NumberProperty;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

// TODO: Nearby Entity Check (this idea skid from "ExHiBitIoN" <- best client ngl)
public class Velocity extends Module {

    private final Queue<TimestampedPacket> packets = new ConcurrentLinkedDeque<>();

    private final NumberProperty<Integer> horizon = new NumberProperty<>("Horizon", 80, 0, 100 , 1);
    private final NumberProperty<Integer> vertical = new NumberProperty<>("Vertical", 80, 0, 100 , 1);
    private final BooleanProperty explosion = new BooleanProperty("Explosion", true);
    private final BooleanProperty legit = new BooleanProperty("Jump", false);
    private final BooleanProperty choke = new BooleanProperty("Choke", false);
    private final BooleanProperty reverseHorizon = new BooleanProperty("Reverse Horizon", false);
    private final BooleanProperty kbAlert = new BooleanProperty("Alert", false);
    private final NumberProperty<Integer> delay = new NumberProperty<>("Choke Delay", 400, 0, 800 , 50);
    public final BooleanProperty antifall = new BooleanProperty("AntiFall", false);
    private final NumberProperty<Integer> limit = new NumberProperty<>("Choke Limit", 8000, 0, 25000 , 500);
    private final BooleanProperty debug = new BooleanProperty("Debug", false);


    public Velocity() {
        super("Velocity", "Reduce your knock-back", ModuleCategory.COMBAT);
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    private final IEventListener<UpdateEvent> onUpdate = e ->
    {
        if (legit.getPropertyValue()) {
            if (mc.thePlayer.hurtTime == 10 && mc.thePlayer.onGround) {
                mc.thePlayer.jump();
            }
        } else
            resetPackets(mc.getNetHandler().getNetworkManager().getNetHandler());
    };


    private final IEventListener<PacketEvent> onPacket = event ->
    {
        //??????????????????????????????
        float hor = horizon.getPropertyValue().floatValue() / 100;
        float ver = vertical.getPropertyValue().floatValue() / 100;

        if (reverseHorizon.getPropertyValue()) hor = -hor;

        if (legit.getPropertyValue()) return; //?????????Legit????????????????????????

        if (event.getPacket() instanceof S27PacketExplosion && explosion.getPropertyValue()) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();

            if (kbAlert.getPropertyValue()) kbAlert(event);

            event.setCancelled(horizon.getPropertyValue() == 0 && vertical.getPropertyValue() == 0);

            //Editing
            ((IS27PacketExplosion) packet).setX(packet.func_149149_c() * hor);
            ((IS27PacketExplosion) packet).setY(packet.func_149144_d() * ver);
            ((IS27PacketExplosion) packet).setZ(packet.func_149147_e() * hor);

            //????????????
            if (debug.getPropertyValue())
                ChatUtils.info("Giga " + (Math.abs(packet.func_149149_c()) + Math.abs(packet.func_149144_d()) + Math.abs(packet.func_149147_e())) * 8000);


            if (!event.isCancelled()) {   //??????????????? ??????????????????Choke??????
                if (choke.getPropertyValue() && //????????????
                        (Math.abs(packet.func_149149_c()) + Math.abs(packet.func_149144_d()) + Math.abs(packet.func_149147_e())) * 8000 < limit.getPropertyValue()) {
                    //????????????????????????
                    event.setCancelled(true);

                    //???????????????Packet
                    addPackets(new TimestampedPacket(event.getPacket(), System.currentTimeMillis()), event);

                }
            }
        }

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            //???????????????????????????????????????
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {

                //?????????????????????
                double x = packet.getMotionX() * hor, y = packet.getMotionY() * ver, z = packet.getMotionZ() * hor;


                if (kbAlert.getPropertyValue()) kbAlert(event);

                //?????????????????? ???????????????
                event.setCancelled(true);

                if (horizon.getPropertyValue() == 0 && vertical.getPropertyValue() == 0) return;

                //????????????
                if (debug.getPropertyValue())
                    ChatUtils.info(String.valueOf(Math.abs(packet.getMotionX()) + Math.abs(packet.getMotionZ()) + Math.abs(packet.getMotionY())));

                if (choke.getPropertyValue() && //??????Choke?????????????????????????????????Choke?????????(???????????????????????????)
                        Math.abs(packet.getMotionX()) + Math.abs(packet.getMotionZ()) + Math.abs(packet.getMotionY()) < limit.getPropertyValue()) {
                    //????????????????????????
                    event.setCancelled(true);

                    //????????????Packet
                    event.setPacket(new S12PacketEntityVelocity(mc.thePlayer.getEntityId(), x / 8000, y / 8000, z / 8000));
                    //????????????????????????Packet
                    addPackets(new TimestampedPacket(event.getPacket(), System.currentTimeMillis()), event);

                } else {
                    //??????KnockBack
                    mc.thePlayer.setVelocity(x / 8000, y / 8000, z / 8000);
                }


            }
        }
    };


    private void addPackets(TimestampedPacket packet, PacketEvent eventReadPacket) {
        synchronized (packets) {
            if (blockPacket(packet.packet)) {
                packets.add(packet);
                eventReadPacket.setCancelled(true);
            }
        }
    }

    private void resetPackets(INetHandler netHandler) {
        if (packets.size() > 0) {
            synchronized (packets) {
                try {
                    for (final TimestampedPacket timestampedPacket : packets) {
                        final long timestamp = timestampedPacket.timestamp;
                        if (Math.abs(timestamp - System.currentTimeMillis()) >= delay.getPropertyValue()) {
                            timestampedPacket.packet.processPacket(netHandler);
                            packets.remove(timestampedPacket);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean blockPacket(Packet<?> packet) {
        return packet instanceof S12PacketEntityVelocity || packet instanceof S27PacketExplosion;
    }

    private static class TimestampedPacket {
        private final Packet<INetHandler> packet;
        private final long timestamp;

        public TimestampedPacket(final Packet<INetHandler> packet, final long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }
    }

    // KB Alert by belris2u
    private void kbAlert(PacketEvent e) {
        if (mc.thePlayer.hurtTime == 0 && mc.thePlayer.ticksExisted > 60) {
            // ??????????????????tick ???????????????????????? ??????????????????1tick????????????
            new Thread(() -> {
                try {
                    Thread.sleep(250);  // After 1 tick
                    if (mc.thePlayer.hurtTime == 0) {   // ????????????????????????????????????
                        if (e.getPacket() instanceof S12PacketEntityVelocity) { // handle S12
                            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.getPacket();
                            ChatUtils.error("You may have been KB checked! S12 #" + mc.thePlayer.ticksExisted);
                            // ????????????cancel??????????????? ?????????????????????cancel????????????setVelocity, ???????????????????????????
                            mc.thePlayer.setVelocity((double) packet.getMotionX() / 8000.0D, (double) packet.getMotionY() / 8000.0D, (double) packet.getMotionZ() / 8000.0D);
                        } else { // handle S27
                            S27PacketExplosion packet = (S27PacketExplosion) e.getPacket();
                            ChatUtils.info("You may have been KB checked! S27 #" + mc.thePlayer.ticksExisted);
                            mc.thePlayer.setVelocity(packet.getX(), packet.getY(), packet.getZ());
                        }
                    }
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            }).start();
        }
    }
}
