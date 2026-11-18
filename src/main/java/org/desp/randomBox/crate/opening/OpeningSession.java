package org.desp.randomBox.crate.opening;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.crate.Crate;
import org.desp.randomBox.crate.CrateItems;
import org.desp.randomBox.crate.CrateReward;
import org.desp.randomBox.crate.CrateText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 인벤토리 오픈 애니메이션 한 번. 보상은 시작 전에 이미 정해져 있고, 회전이 끝나면 당첨 칸에 그 보상이 멈춘다.
 * 창을 닫거나 접속을 끊어도 보상은 그 자리에서 바로 지급된다.
 */
public class OpeningSession implements InventoryHolder {

    private static final Map<UUID, OpeningSession> ACTIVE = new ConcurrentHashMap<>();

    private final Player player;
    private final Crate crate;
    private final OpeningConfig config;
    private final ItemStack realItem;
    private final List<ItemStack> rewardPool = new ArrayList<>();
    private final Runnable onComplete;
    private final Inventory inventory;
    private final List<RunState> states = new ArrayList<>();

    private BukkitTask task;
    private int tick;
    private int finishTick = -1;
    private boolean done;

    private static final class RunState {
        final OpeningConfig.SpinnerRun run;
        final int total;
        /** SEQUENTIAL 에서 실제 보상을 밀어 넣을 회차(1부터). 0이면 해당 없음. */
        int injectAt;
        /** INDEPENDENT/SYNCHRONIZED 에서 마지막 회차에 실제 보상을 놓을 칸. -1이면 해당 없음. */
        int winSlot = -1;
        int phase;
        int doneInPhase;
        int spinsDone;
        int nextTick;
        boolean finished;

        RunState(OpeningConfig.SpinnerRun run) {
            this.run = run;
            this.total = run.totalSpins();
            this.nextTick = run.getDelay();
            this.finished = total <= 0;
        }
    }

    private OpeningSession(Player player, Crate crate, OpeningConfig config, CrateReward reward, Runnable onComplete) {
        this.player = player;
        this.crate = crate;
        this.config = config;
        this.realItem = reward.previewItem();
        this.onComplete = onComplete;
        String title = config.getTitle().replace("%crate_name%", "<crate_name>");
        this.inventory = Bukkit.createInventory(this, config.getSize(),
                CrateText.mm(title, CrateText.parsed("crate_name", crate.getName())));

        for (OpeningConfig.StaticContent content : config.getContents()) {
            ItemStack item = CrateItems.decor(content.getItem());
            for (int slot : content.getSlots()) {
                if (slot < inventory.getSize()) {
                    inventory.setItem(slot, item);
                }
            }
        }

        for (OpeningConfig.SpinnerRun run : config.getRuns()) {
            RunState state = new RunState(run);
            if (run.isReward()) {
                for (int win : config.getWinSlots()) {
                    int index = indexOf(run.getSlots(), win);
                    if (index < 0) {
                        continue;
                    }
                    if (run.getMode() == OpeningConfig.Mode.SEQUENTIAL) {
                        state.injectAt = Math.max(1, state.total - index);
                    } else {
                        state.winSlot = win;
                    }
                    break;
                }
            }
            states.add(state);
        }
    }

    public static boolean isOpening(Player player) {
        return ACTIVE.containsKey(player.getUniqueId());
    }

    public static OpeningSession get(Player player) {
        return ACTIVE.get(player.getUniqueId());
    }

    /** 애니메이션을 시작한다. 설정이 없으면 false 를 돌려주고 아무 일도 하지 않는다. */
    public static boolean start(Player player, Crate crate, OpeningConfig config, CrateReward reward, Runnable onComplete) {
        if (config == null) {
            return false;
        }
        OpeningSession session = new OpeningSession(player, crate, config, reward, onComplete);
        for (CrateReward candidate : crate.getRewards().values()) {
            session.rewardPool.add(candidate.previewItem());
        }
        if (session.rewardPool.isEmpty()) {
            session.rewardPool.add(session.realItem);
        }
        ACTIVE.put(player.getUniqueId(), session);
        player.openInventory(session.inventory);
        session.task = Bukkit.getScheduler().runTaskTimer(RandomBox.getInstance(), session::tick, 1L, 1L);
        return true;
    }

    /** 서버 종료 등으로 남은 애니메이션을 모두 끝내고 보상을 지급한다. */
    public static void finishAll() {
        for (OpeningSession session : new ArrayList<>(ACTIVE.values())) {
            session.finish(true);
        }
    }

    private void tick() {
        if (done) {
            return;
        }
        if (!player.isOnline()) {
            finish(false);
            return;
        }
        if (finishTick >= 0) {
            if (tick >= finishTick) {
                finish(true);
            }
            tick++;
            return;
        }

        boolean allFinished = true;
        for (RunState state : states) {
            if (state.finished) {
                continue;
            }
            allFinished = false;
            if (tick < state.nextTick) {
                continue;
            }
            spin(state);
            state.spinsDone++;
            state.doneInPhase++;
            int[] phase = state.run.getSpins().get(state.phase);
            if (state.doneInPhase >= phase[0]) {
                state.phase++;
                state.doneInPhase = 0;
            }
            if (state.spinsDone >= state.total || state.phase >= state.run.getSpins().size()) {
                state.finished = true;
            } else {
                state.nextTick = tick + state.run.getSpins().get(state.phase)[1];
            }
        }

        if (allFinished) {
            // 어떤 회전에도 당첨 칸이 없었다면 마지막에 직접 놓는다.
            for (int win : config.getWinSlots()) {
                if (win < inventory.getSize()) {
                    inventory.setItem(win, realItem);
                }
            }
            finishTick = tick + Math.max(1, config.getCompletionPause());
        }
        tick++;
    }

    private void spin(RunState state) {
        OpeningConfig.SpinnerRun run = state.run;
        int[] slots = run.getSlots();
        int spinNumber = state.spinsDone + 1;
        boolean last = spinNumber >= state.total;

        if (slots.length > 0) {
            switch (run.getMode()) {
                case SEQUENTIAL -> {
                    for (int i = slots.length - 1; i > 0; i--) {
                        setSlot(slots[i], inventory.getItem(slots[i - 1]));
                    }
                    ItemStack next = (run.isReward() && spinNumber == state.injectAt) ? realItem : nextItem(run);
                    setSlot(slots[0], next);
                }
                case SYNCHRONIZED -> {
                    ItemStack next = nextItem(run);
                    for (int slot : slots) {
                        setSlot(slot, next);
                    }
                }
                default -> {
                    for (int slot : slots) {
                        setSlot(slot, nextItem(run));
                    }
                }
            }
            if (run.isReward() && last && state.winSlot >= 0) {
                setSlot(state.winSlot, realItem);
            }
        }

        if (run.getSound() != null) {
            player.playSound(player.getLocation(), run.getSound(), run.getVolume(), run.getPitch());
        }
    }

    private void setSlot(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private ItemStack nextItem(OpeningConfig.SpinnerRun run) {
        if (run.isReward()) {
            return rewardPool.get(ThreadLocalRandom.current().nextInt(rewardPool.size()));
        }
        List<OpeningConfig.DecorItem> items = config.getAnimationSpinners().get(run.getSpinnerId());
        if (items == null || items.isEmpty()) {
            return null;
        }
        double total = 0;
        for (OpeningConfig.DecorItem item : items) {
            total += Math.max(0, item.getChance());
        }
        double value = ThreadLocalRandom.current().nextDouble(Math.max(total, 0.0001));
        double cumulative = 0;
        for (OpeningConfig.DecorItem item : items) {
            cumulative += Math.max(0, item.getChance());
            if (value < cumulative) {
                return CrateItems.decor(item);
            }
        }
        return CrateItems.decor(items.get(items.size() - 1));
    }

    /** 애니메이션을 끝내고 보상을 지급한다. 여러 번 불려도 한 번만 지급한다. */
    public void finish(boolean closeInventory) {
        if (done) {
            return;
        }
        done = true;
        if (task != null) {
            task.cancel();
        }
        ACTIVE.remove(player.getUniqueId(), this);
        if (closeInventory && player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == this) {
            player.closeInventory();
        }
        onComplete.run();
    }

    private static int indexOf(int[] slots, int value) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == value) {
                return i;
            }
        }
        return -1;
    }

    public Crate getCrate() {
        return crate;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
