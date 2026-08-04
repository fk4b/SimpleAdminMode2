package fallen;

import arc.struct.Queue;
import arc.util.Log;
import mindustry.Vars;

public class ActionsHistory {
    private static final int MAX_HISTORY_SIZE = 50000;
    public static Queue<BlockPlayerPlan> blocksplayersplans = new LimitedQueue<BlockPlayerPlan>(50000, "BlocksBuild");
    public static Queue<BlockConfigPlayerPlan> blockconfplayersplans = new LimitedQueue<BlockConfigPlayerPlan>(50000, "BlockConfigs");

    public static void clearactionhistory() {
        blocksplayersplans.clear();
        blockconfplayersplans.clear();
        ((LimitedQueue)blocksplayersplans).resetWarning();
        ((LimitedQueue)blockconfplayersplans).resetWarning();
    }

    public static class LimitedQueue<T>
    extends Queue<T> {
        private final int limit;
        private final String name;
        private boolean hasWarned = false;

        public LimitedQueue(int limit, String name) {
            this.limit = limit;
            this.name = name;
        }

        public void resetWarning() {
            this.hasWarned = false;
        }

        public void addLast(T object) {
            super.addLast(object);
            this.checkLimit(true);
        }

        public void addFirst(T object) {
            super.addFirst(object);
            this.checkLimit(false);
        }

        public void checkLimit(boolean isLastAdd) {
            if (this.size > this.limit) {
                if (isLastAdd) {
                    this.removeFirst();
                } else {
                    this.removeLast();
                }
                if (!this.hasWarned) {
                    Log.info((Object)("[ActionsHistory] Очередь '" + this.name + "' заполнена (" + this.limit + ")"));
                    if (Vars.ui != null && Vars.ui.hudfrag != null) {
                        Vars.ui.hudfrag.showToast("[orange]История " + this.name + " заполнена!");
                    }
                    this.hasWarned = true;
                }
            }
        }
    }

    public static class BlockConfigPlayerPlan {
        public final short x;
        public final short y;
        public final short block;
        /** Normalized nick (no colors / freeze icons). */
        public final String lastacs;
        /** Player entity id for stable matching after nick changes (freeze icon). */
        public final int playerId;
        public final long timestamp;

        public BlockConfigPlayerPlan(int x, int y, short block, String lastacs, int playerId) {
            this.x = (short)x;
            this.y = (short)y;
            this.block = block;
            this.lastacs = lastacs;
            this.playerId = playerId;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class BlockPlayerPlan {
        public final short x;
        public final short y;
        public final short rotation;
        public final short block;
        /** Normalized nick (no colors / freeze icons). */
        public final String lastacs;
        public final Object config;
        public final long timestamp;
        public boolean wasbreaking;
        /** Player entity id for stable matching after nick changes (freeze icon). */
        public final int playerId;

        public BlockPlayerPlan(int x, int y, short rotation, short block, Object config, String lastacs, boolean wasbreaking, int playerId) {
            this.x = (short)x;
            this.y = (short)y;
            this.rotation = rotation;
            this.block = block;
            this.config = config;
            this.lastacs = lastacs;
            this.wasbreaking = wasbreaking;
            this.playerId = playerId;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
