/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  arc.Core
 *  arc.input.KeyCode
 *  arc.math.geom.Position
 *  arc.math.geom.Vec2
 *  mindustry.Vars
 *  mindustry.gen.Unit
 *  mindustry.input.MobileInput
 *  mindustry.input.PlaceMode
 */
package fallen;

import arc.Core;
import arc.input.KeyCode;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.input.MobileInput;
import mindustry.input.PlaceMode;

public class FreeCamMobileInput
extends MobileInput {
    public boolean freeCamActive = false;
    private final Vec2 camAnchor = new Vec2();

    public void update() {
        super.update();
        if (this.freeCamActive) {
            if (Vars.player.unit() != null) {
                Vars.player.unit().vel.setZero();
                Vars.player.shooting = false;
            }
            this.targetPos.set(Vars.player.x, Vars.player.y);
            this.movement.setZero();
        }
    }

    protected void updateMovement(Unit unit) {
        if (!this.freeCamActive) {
            super.updateMovement(unit);
        } else {
            unit.aimLook(Vars.player.mouseX, Vars.player.mouseY);
            unit.controlWeapons(false, false);
        }
    }

    public void spectate(Unit unit) {
        if (this.freeCamActive && unit != null) {
            this.camAnchor.set((Position)unit);
            Core.camera.position.set(this.camAnchor);
        }
        super.spectate(unit);
    }

    public boolean pan(float x, float y, float deltaX, float deltaY) {
        if (this.freeCamActive && (this.lineMode || this.schematicMode || this.selecting || this.mode != PlaceMode.none || this.droppingItem)) {
            return super.pan(x, y, deltaX, deltaY);
        }
        if (!this.freeCamActive) {
            return super.pan(x, y, deltaX, deltaY);
        }
        if (Core.scene.hasMouse(x, y)) {
            return false;
        }
        float scale = Core.camera.width / (float)Core.graphics.getWidth();
        this.camAnchor.x -= (deltaX *= scale);
        this.camAnchor.y -= (deltaY *= scale);
        Core.camera.position.set(this.camAnchor);
        Core.camera.position.clamp(-Core.camera.width / 2.0f, -Core.camera.height / 2.0f, (float)Vars.world.unitWidth() + Core.camera.width / 2.0f, (float)Vars.world.unitHeight() + Core.camera.height / 2.0f);
        return true;
    }

    public boolean panStop(float x, float y, int pointer, KeyCode button) {
        if (this.freeCamActive) {
            return true;
        }
        return super.panStop(x, y, pointer, button);
    }

    public void setFreeCam(boolean active) {
        if (this.freeCamActive == active) {
            return;
        }
        this.freeCamActive = active;
        if (active) {
            this.camAnchor.set(Core.camera.position);
        }
    }

    public boolean isFreeCam() {
        return this.freeCamActive;
    }
}
