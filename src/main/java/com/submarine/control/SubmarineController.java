package com.submarine.control;

import com.submarine.data.SubmarineMetadata;
import com.submarine.data.SubmarineSavedData;
import com.submarine.entity.SubmarineSeatEntity;
import com.submarine.net.SubmarineInput;
import com.submarine.net.SubmarineNetworking;
import com.submarine.template.StarterSubmarineTemplate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter;

public final class SubmarineController {
    private SubmarineController() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SubmarineController::tickLevel);
    }

    private static void tickLevel(ServerLevel level) {
        for (SubmarineMetadata metadata : SubmarineSavedData.get(level).all()) {
            if (!metadata.dimensionId().equals(VSGameUtilsKt.getDimensionId(level))) {
                continue;
            }
            tickSubmarine(level, metadata);
        }
    }

    private static void tickSubmarine(ServerLevel level, SubmarineMetadata metadata) {
        ServerShip ship = VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(metadata.shipId());
        if (ship == null) {
            SubmarineNetworking.clearInput(metadata.shipId());
            return;
        }

        SubmarineInput input = SubmarineNetworking.getInput(metadata.shipId());
        boolean hasFreshPilot = input != null
                && level.getGameTime() - input.tick() <= StarterSubmarineTemplate.IDLE_STATIC_AFTER_TICKS
                && isPilotStillMounted(level, metadata.shipId(), input);

        GameToPhysicsAdapter forces = ValkyrienSkiesMod.getOrCreateGTPA(VSGameUtilsKt.getDimensionId(level));
        boolean underwater = ship.getTransform().getPositionInWorld().y() < level.getSeaLevel();

        if (!hasFreshPilot) {
            if (underwater) {
                forces.setStatic(ship.getId(), false);
                applyBuoyancy(ship, forces);
            } else {
                forces.setStatic(ship.getId(), true);
            }
            SubmarineNetworking.clearInput(metadata.shipId());
            return;
        }

        forces.setStatic(ship.getId(), false);
        if (underwater) {
            applyBuoyancy(ship, forces);
        }
        applyPilotForces(ship, forces, input);
    }

    private static void applyBuoyancy(ServerShip ship, GameToPhysicsAdapter forces) {
        double mass = ship.getInertiaData().getMass();
        double y = ship.getTransform().getPositionInWorld().y();
        double multiplier = y < StarterSubmarineTemplate.MIN_DEPTH ? 4.0 : 1.0;
        forces.applyWorldForce(ship.getId(),
                new Vector3d(0.0, mass * StarterSubmarineTemplate.BUOYANCY_GRAVITY * multiplier, 0.0),
                null);
    }

    private static boolean isPilotStillMounted(ServerLevel level, long shipId, SubmarineInput input) {
        return level.getPlayerByUUID(input.pilot()) != null
                && level.getPlayerByUUID(input.pilot()).getVehicle() instanceof SubmarineSeatEntity seat
                && seat.isPilotSeat()
                && seat.getShipId() == shipId;
    }

    private static void applyPilotForces(ServerShip ship, GameToPhysicsAdapter forces, SubmarineInput input) {
        Vector3d forward = ship.getTransform().getShipToWorld()
                .transformDirection(new Vector3d(-1.0, 0.0, 0.0))
                .normalize()
                .mul(input.forward() * StarterSubmarineTemplate.FORWARD_FORCE);
        Vector3d vertical = new Vector3d(0.0, input.vertical() * StarterSubmarineTemplate.VERTICAL_FORCE, 0.0);
        Vector3d drag = new Vector3d(ship.getVelocity()).mul(-StarterSubmarineTemplate.LINEAR_DRAG);
        Vector3d angularDrag = new Vector3d(ship.getAngularVelocity()).mul(-StarterSubmarineTemplate.ANGULAR_DRAG);
        Vector3d yawTorque = new Vector3d(0.0, input.turn() * StarterSubmarineTemplate.YAW_TORQUE, 0.0);

        forces.applyWorldForce(ship.getId(), forward.add(vertical).add(drag), null);
        forces.applyWorldTorque(ship.getId(), yawTorque.add(angularDrag));
    }
}
