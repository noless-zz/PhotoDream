package com.noam.photodream.motion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Photos move like a school of fish, using Craig Reynolds' "boids" (1986). Each photo only
 * follows three simple rules about its neighbours, yet together they swim as a group:
 * <ol>
 *   <li><b>Separation</b> – don't bump into the ones right next to you.</li>
 *   <li><b>Alignment</b> – swim the same way as your neighbours.</li>
 *   <li><b>Cohesion</b> – stay close to the group.</li>
 * </ol>
 * Plus a fourth: turn back before reaching the edge of the screen.
 *
 * Unlike the other patterns this one has memory (each fish's position and speed), so it is
 * simulated one small step at a time. The first {@link #pose} call of each new frame time moves
 * every fish; fish whose photo has left the table are forgotten. Positions are kept in a
 * 0..1 × 0..1 box and stretched to the table, so a screen rotation does not scatter the school.
 */
public final class FlockMotion implements MotionPattern {

    static final double MAX_SPEED = 0.12;      // box widths per second
    static final double MIN_SPEED = 0.04;
    static final double NEIGHBOUR = 0.30;      // how far a fish can "see"
    static final double PERSONAL = 0.16;       // closer than this is too close
    static final double MARGIN = 0.12;         // start turning back this far from the edge
    static final double MAX_STEP = 0.1;        // seconds; longer gaps are simulated as this

    private static final class Fish {
        double x, y, vx, vy;
        double lastSeen;
    }

    private final Map<Long, Fish> school = new HashMap<>();
    private double simTime = Double.NaN;

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        if (Double.isNaN(simTime) || f.time < simTime) {
            simTime = f.time;                  // first frame, or the clock went back: start here
        } else if (f.time > simTime) {
            step(Math.min(MAX_STEP, f.time - simTime), simTime);
            simTime = f.time;
        }

        Fish fish = school.get(id);
        if (fish == null) fish = spawn(id);
        fish.lastSeen = f.time;

        double heading = Math.atan2(fish.vy * f.height, fish.vx * f.width);
        out.set((float) (fish.x * f.width), (float) (fish.y * f.height),
                (float) (8 * Math.sin(heading)),           // bank into the turn a little
                1f, 1f, 0f);
    }

    /** A new fish starts somewhere random (but the same for the same id) and swims off. */
    private Fish spawn(long id) {
        Random r = new Random(id * 7919L + 17);
        Fish fish = new Fish();
        fish.x = 0.2 + 0.6 * r.nextDouble();
        fish.y = 0.2 + 0.6 * r.nextDouble();
        double dir = r.nextDouble() * 2 * Math.PI;
        fish.vx = MIN_SPEED * 1.5 * Math.cos(dir);
        fish.vy = MIN_SPEED * 1.5 * Math.sin(dir);
        school.put(id, fish);
        return fish;
    }

    private void step(double dt, double previousFrame) {
        // forget photos that were not asked for in the previous frame (they left the table)
        for (Iterator<Fish> it = school.values().iterator(); it.hasNext(); ) {
            if (it.next().lastSeen < previousFrame) it.remove();
        }

        for (Fish a : school.values()) {
            double sepX = 0, sepY = 0, aliX = 0, aliY = 0, cohX = 0, cohY = 0;
            int near = 0;
            for (Fish b : school.values()) {
                if (a == b) continue;
                double dx = b.x - a.x, dy = b.y - a.y;
                double d = Math.hypot(dx, dy);
                if (d > NEIGHBOUR) continue;
                near++;
                aliX += b.vx;
                aliY += b.vy;
                cohX += b.x;
                cohY += b.y;
                if (d < PERSONAL && d > 1e-6) {
                    double push = (PERSONAL - d) / PERSONAL;   // the closer, the harder
                    sepX -= dx / d * push;
                    sepY -= dy / d * push;
                }
            }
            double ax = 0.9 * sepX, ay = 0.9 * sepY;
            if (near > 0) {
                ax += 0.6 * (aliX / near - a.vx) + 0.25 * (cohX / near - a.x);
                ay += 0.6 * (aliY / near - a.vy) + 0.25 * (cohY / near - a.y);
            }
            // the walls of the tank
            if (a.x < MARGIN) ax += (MARGIN - a.x) * 4;
            if (a.x > 1 - MARGIN) ax -= (a.x - (1 - MARGIN)) * 4;
            if (a.y < MARGIN) ay += (MARGIN - a.y) * 4;
            if (a.y > 1 - MARGIN) ay -= (a.y - (1 - MARGIN)) * 4;

            a.vx += ax * dt;
            a.vy += ay * dt;
            double speed = Math.hypot(a.vx, a.vy);
            double limited = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
            if (speed > 1e-9) {
                a.vx *= limited / speed;
                a.vy *= limited / speed;
            } else {
                a.vx = limited;
            }
        }
        for (Fish a : school.values()) {
            a.x = clamp(a.x + a.vx * dt);
            a.y = clamp(a.y + a.vy * dt);
        }
    }

    private static double clamp(double v) {
        return Math.max(0.06, Math.min(0.94, v));
    }

    /** For tests: how many fish are swimming. */
    int size() {
        return school.size();
    }
}
