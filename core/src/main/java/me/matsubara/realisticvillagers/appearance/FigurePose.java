package me.matsubara.realisticvillagers.appearance;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Torso placement in the local coordinates of a FIXED item display passenger. */
final class FigurePose {
    static final float MODEL_SCALE = 0.9375f;
    static final float TORSO_TOP = 1.501f;
    // The jacket is inflated by a quarter pixel. The model roots overlap this surface.
    static final float FRONT = 2.25f / 16.0f;

    /**
     * How far back along its bed the game lays a sleeping body, in blocks.
     * <p>
     * Not a number anyone here chose. A sleeping entity is put at the head block of its bed and
     * would otherwise be drawn lying out of it from there, so the renderer slides the whole model
     * against the bed's facing by a standing eye height less a tenth — which puts the pillow under
     * the head and the feet at the other end, where they belong.
     * <p>
     * It was written here as the tenth alone. The eye height it is subtracted from had been dropped,
     * so the figure was slid four centimetres where the body it belongs to slid a block and a half,
     * and the two parted company by exactly the difference. That does not read as a figure slightly
     * out of place: it reads as a piece of somebody lying in the grass beside the bed, which is what
     * it was.
     */
    private static final float BED_LENGTH = 1.62f - 0.1f;

    record Placement(Vector3f chest, Quaternionf rotation, float seat) {}

    private FigurePose() {}

    /**
     * @param pose  the posture the body is <em>drawn</em> in, which is what every line below places
     *              against.
     * @param seat  the posture the client seats the figure by, which is the posture of whatever it
     *              is actually riding. Ordinarily the same as the first, and not the same at all
     *              when somebody else is drawing her: a posing plugin lays a stand-in of her in a
     *              bed and leaves the woman the figure rides standing invisible where she was, so
     *              the chest is a sleeper's and the seat is a stander's.
     */
    static Placement place(Pose pose, Pose seat, double band, float stature, float lean, float blend,
                           float roll, float pitch, int age, BlockFace bed, float clearance) {
        boolean sleeping = pose == Pose.SLEEPING;
        boolean swimming = pose == Pose.SWIMMING;
        boolean flying = pose == Pose.FALL_FLYING;
        boolean spinning = pose == Pose.SPIN_ATTACK;
        boolean crouching = pose == Pose.SNEAKING;
        double lift = 0.0d;

        // How far past the jacket what she is wearing reaches. A number rather than a yes or no,
        // because there are two layers of armour and they are not the same thickness.
        double forward = clearance;

        Quaternionf turn = new Quaternionf();
        if (sleeping) {
            turn.rotateY((float) Math.toRadians(bedAngle(bed)))
                    .rotateZ((float) (Math.PI / 2.0d)).rotateY((float) (Math.PI * 1.5d));
            lift = 0.0d;
        } else if (spinning) {
            turn.rotateX((float) Math.toRadians(-90.0f - pitch))
                    .rotateY((float) Math.toRadians((age % 24) * -75.0f));
        } else {
            turn.rotateX((float) Math.toRadians(lean));
            if (flying) turn.rotateY(roll);
        }

        // Work in the player's rendered body coordinates until the final conversion.
        Vector3f chest = new Vector3f(0.0f, (float) (band + lift), -(float) (FRONT + forward));
        if (crouching) {
            Quaternionf bend = new Quaternionf().rotateX(-0.5f);
            chest.sub(0.0f, TORSO_TOP, 0.0f).rotate(bend)
                    .add(0.0f, TORSO_TOP - 3.2f / 16.0f, 0.0f);
            // ModelPart rotates before the renderer's mirror; its X rotation changes sign.
            turn.mul(bend);
        }
        chest.mul(MODEL_SCALE);
        // This translation precedes PlayerRenderer.scale(), so it must not get its 0.9375 factor.
        if (swimming && !spinning) chest.add(0.0f, -1.0f, 0.3f);
        chest.rotate(crouching ? new Quaternionf().rotateX((float) Math.toRadians(lean)) : turn)
                .mul(stature);
        if (crouching) chest.y -= 0.125f * stature;
        if (sleeping && bed != null) {
            // Hers, like everything else here. The slide is a length of body, and a body drawn at
            // another size slides by another length — the game takes it from an eye height, and an
            // eye height grows and shrinks with her.
            float along = BED_LENGTH * stature;
            chest.add(-bed.getModX() * along, 0.0f, -bed.getModZ() * along);
        }

        // DisplayRenderer uses -yaw, LivingEntityRenderer uses 180-yaw, and the item
        // renderer adds its own Y(180) AFTER our transform. Convert both the anchor and
        // the rotation: H * chest, H * turn * H^-1. Converting only one detaches the mesh.
        chest.x = -chest.x;
        chest.z = -chest.z;
        return new Placement(chest, displayRotation(turn), passengerSeat(seat, stature));
    }

    static float passengerSeat(Pose pose, float stature) {
        // Every one of these is the height of the hitbox the pose gives her, and the client seats a
        // passenger by that hitbox — which the scale attribute grows and shrinks along with her. So
        // every one of them is hers. Sleeping was the exception and there was no reason for it to be
        // one: a villager is the only body here that is ever a size other than ordinary, and she is
        // also the only one whose figure was left an inch off her while she slept.
        return switch (pose) {
            case SLEEPING -> 0.2f * stature;
            case SWIMMING, FALL_FLYING, SPIN_ATTACK -> 0.6f * stature;
            case SNEAKING -> 1.5f * stature;
            default -> 1.8f * stature;
        };
    }

    static Quaternionf displayRotation(Quaternionf bodyTurn) {
        return new Quaternionf(-bodyTurn.x, bodyTurn.y, -bodyTurn.z, bodyTurn.w);
    }

    static Vector3f tissueOffset(Quaternionf rotation, float displacement) {
        return new Vector3f(0.0f, displacement, 0.0f).rotate(rotation);
    }

    /**
     * How big a body addition is once it has settled into the posture she is in.
     * <p>
     * Standing, a figure is the size she has. Lying down it is not: soft tissue has no frame of its
     * own and does whatever her weight and the ground tell it to. On her back it settles towards her
     * spine and spreads across her ribs; face down — crawling, swimming, gliding — the same weight
     * presses it flat against whatever is under her. Both come out as much less standing out of her
     * and a little more across her, which is the one thing the two have in common and the only thing
     * worth drawing.
     * <p>
     * It used to be asked of the sleeping pose alone, and asked as a yes or no. That is exactly what
     * left a bust standing up out of a body lying flat: crawling and gliding never reached this at
     * all, so a woman dragging herself under a slab kept her full standing profile pointing at the
     * floor. Now every posture that lays her out says how far it has, and the settling follows that
     * — the same number the body itself is being turned by, so the two arrive together.
     *
     * @param flat nought upright, one flat, and anywhere between while she is on her way to either.
     */
    static Vector3f figureScale(boolean build, float across, float depth, float flat) {
        float laid = Math.max(0.0f, Math.min(1.0f, flat));
        if (laid <= 0.0f) return new Vector3f(across, across, depth);

        // A stomach keeps most of what it has — it is carried in front either way and there is a
        // frame of ribs and hips holding it there. A bust has neither, and settles about half.
        //
        // Half rather than the quarter it was. Tissue lying down does go soft, and the first attempt
        // at this was answering a figure that stood a full two and a half pixels out of a four pixel
        // torso — against that, a quarter was a correction. Against shapes a third shallower it is
        // not a correction, it is an erasure: what is left is a woman with nothing there at all,
        // which is a different fault from the one being fixed and just as visible. So it sits
        // between the two, close to the projection the old deep shapes had after settling, which
        // was the one part of them nobody complained about.
        //
        // The spread is deliberately small, and smaller than it reads as being right. A torso is
        // eight pixels across and so is every shape here, so the two are the same width to within a
        // fraction of a pixel; anything that widens one meaningfully hangs it over the edge of the
        // other, and a sliver of chest poking past her ribs while she lies still is a worse fault
        // than the one being fixed. What sells a body gone soft is the depth going, not the width
        // arriving.
        float settled = build ? 0.83f : 0.55f;
        float spread = build ? 1.03f : 1.1f;
        float shorten = build ? 1.0f : 0.92f;

        return new Vector3f(
                across * ease(1.0f, spread, laid),
                across * ease(1.0f, shorten, laid),
                depth * ease(1.0f, settled, laid));
    }

    private static float ease(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    /** Poses whose torso crosses the first-person camera. */
    static boolean crossesFirstPersonCamera(Pose pose, float lean) {
        if (lean != 0.0f) return true;
        return switch (pose) {
            case FALL_FLYING, SWIMMING, SPIN_ATTACK -> true;
            default -> false;
        };
    }

    static float flightRoll(double dx, double dz, float yaw, float pitch) {
        double horizontalView = Math.cos(Math.toRadians(pitch));
        if (dx * dx + dz * dz <= 1.0E-5d || horizontalView * horizontalView <= 1.0E-5d) return 0.0f;
        double length = Math.hypot(dx, dz);
        double vx = -Math.sin(Math.toRadians(yaw));
        double vz = Math.cos(Math.toRadians(yaw));
        double dot = (dx * vx + dz * vz) / length;
        double cross = dx * vz - dz * vx;
        // Mojang uses abs(dot): a backwards glide does not roll the player upside down.
        return (float) (Math.signum(cross) * Math.acos(Math.min(1.0d, Math.abs(dot))));
    }

    private static float bedAngle(BlockFace facing) {
        if (facing == null) return 0.0f;
        return switch (facing) {
            case SOUTH -> 90.0f;
            case WEST -> 0.0f;
            case NORTH -> 270.0f;
            case EAST -> 180.0f;
            default -> 0.0f;
        };
    }
}
