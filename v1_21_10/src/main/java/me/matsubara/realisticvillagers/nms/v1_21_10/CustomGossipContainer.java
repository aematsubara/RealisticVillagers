package me.matsubara.realisticvillagers.nms.v1_21_10;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.Getter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class CustomGossipContainer extends GossipContainer {

    private final @Getter Map<UUID, EntityGossips> gossips = Maps.newHashMap();

    private static final int DISCARD_THRESHOLD = 2;
    private static final Map<GossipType, Integer> MAX_GOSSIP_TYPE_TRANSFER = ImmutableMap.of(
            GossipType.MAJOR_NEGATIVE, 15,
            GossipType.MINOR_NEGATIVE, 30,
            GossipType.MINOR_POSITIVE, 30,
            GossipType.MAJOR_POSITIVE, 15,
            GossipType.TRADING, 5);

    public CustomGossipContainer() {

    }

    public CustomGossipContainer(@NotNull List<GossipEntry> list) {
        list.forEach((entry) -> getOrCreate(entry.target).entries.put(entry.type, entry.value));
    }

    @VisibleForDebug
    @Override
    public Map<UUID, Object2IntMap<GossipType>> getGossipEntries() {
        Map<UUID, Object2IntMap<GossipType>> entries = new HashMap<>();
        for (Map.Entry<UUID, EntityGossips> entry : gossips.entrySet()) {
            entries.put(entry.getKey(), entry.getValue().entries);
        }
        return entries;
    }

    @Override
    public void decay() {
        Iterator<EntityGossips> iterator = gossips.values().iterator();
        while (iterator.hasNext()) {
            EntityGossips gossips = iterator.next();
            gossips.decay();
            if (gossips.isEmpty()) iterator.remove();
        }
    }

    public Stream<GossipEntry> unpack() {
        return gossips.entrySet().stream().flatMap((entry) -> entry.getValue().unpack(entry.getKey()));
    }

    private Collection<GossipEntry> selectGossipsForTransfer(RandomSource random, int topics) {
        List<GossipEntry> entries = unpack().toList();
        if (entries.isEmpty()) return Collections.emptyList();

        int[] array = new int[entries.size()];
        int weighted = 0;

        for (int i = 0; i < entries.size(); i++) {
            GossipEntry entry = entries.get(i);

            weighted += Math.abs(entry.weightedValue());
            array[i] = weighted - 1;
        }

        Set<GossipEntry> transfer = Sets.newIdentityHashSet();

        for (int i = 0; i < topics; i++) {
            int which = Arrays.binarySearch(array, random.nextInt(weighted));

            GossipEntry temp = entries.get(which < 0 ? -which - 1 : which);
            if (temp == null) continue;

            int amount = Math.min(Math.abs(temp.weightedValue()), MAX_GOSSIP_TYPE_TRANSFER.get(temp.type()));
            transfer.add(new GossipEntry(temp.target(), temp.type(), amount / temp.type().weight));
        }

        return transfer;
    }

    private EntityGossips getOrCreate(UUID uuid) {
        return gossips.computeIfAbsent(uuid, (created) -> new EntityGossips());
    }

    public void transferFrom(@NotNull CustomGossipContainer container, RandomSource random, int topics) {
        Collection<GossipEntry> entries = container.selectGossipsForTransfer(random, topics);
        entries.forEach((entry) -> {
            int transfer = entry.value - entry.type.decayPerTransfer;
            if (transfer >= DISCARD_THRESHOLD) {
                getOrCreate(entry.target).entries.mergeInt(entry.type, transfer, Math::max);
            }
        });
    }

    @Override
    public int getReputation(UUID uuid, Predicate<GossipType> predicate) {
        EntityGossips gossips = this.gossips.get(uuid);
        return gossips != null ? gossips.weightedValue(predicate) : 0;
    }

    @Override
    public long getCountForType(GossipType type, DoublePredicate predicate) {
        return gossips.values().stream()
                .filter((gossips) -> predicate.test(gossips.entries.getOrDefault(type, 0) * type.weight))
                .count();
    }

    @Override
    public void add(UUID uuid, GossipType type, int amount) {
        EntityGossips gossips = getOrCreate(uuid);
        gossips.entries.mergeInt(type, amount, Integer::sum);
        gossips.makeSureValueIsntTooLow(type);
        if (gossips.isEmpty()) this.gossips.remove(uuid);
    }

    @Override
    public void remove(UUID uuid, GossipType type, int amount, Villager.ReputationEvent event) {
        // Incompatible with Paper.
    }

    @SuppressWarnings("unused")
    public void remove(UUID uuid, GossipType type, int amount) {
        add(uuid, type, -amount);
    }

    @Override
    public void remove(UUID uuid, GossipType type, Villager.ReputationEvent event) {
        // Incompatible with Paper.
    }

    @SuppressWarnings("unused")
    public void remove(UUID uuid, GossipType type) {
        EntityGossips gossips = this.gossips.get(uuid);
        if (gossips == null) return;

        gossips.remove(type);
        if (gossips.isEmpty()) this.gossips.remove(uuid);
    }

    @Override
    public void remove(GossipType type, Villager.ReputationEvent event) {
        // Incompatible with Paper.
    }

    public void remove(GossipType type) {
        Iterator<EntityGossips> iterator = gossips.values().iterator();

        while (iterator.hasNext()) {
            EntityGossips gossips = iterator.next();
            gossips.remove(type);
            if (gossips.isEmpty()) iterator.remove();
        }
    }

    public void clear() {
        gossips.clear();
    }

    @Override
    public void putAll(@NotNull GossipContainer reputation) {
        reputation.getGossipEntries().forEach((uuid, type) -> getOrCreate(uuid).entries.putAll(type));
    }

    private static class EntityGossips {

        private final Object2IntMap<GossipType> entries = new Object2IntOpenHashMap<>();

        public int weightedValue(Predicate<GossipType> predicate) {
            return entries.object2IntEntrySet().stream()
                    .filter((entry) -> predicate.test(entry.getKey()))
                    .mapToInt((entry) -> entry.getIntValue() * entry.getKey().weight)
                    .sum();
        }

        public Stream<GossipEntry> unpack(UUID uuid) {
            return entries.object2IntEntrySet().stream().map((entry) -> new GossipEntry(uuid, entry.getKey(), entry.getIntValue()));
        }

        public void decay() {
            ObjectIterator<Object2IntMap.Entry<GossipType>> iterator = entries.object2IntEntrySet().iterator();

            while (iterator.hasNext()) {
                Object2IntMap.Entry<GossipType> entry = iterator.next();

                int amount = entry.getIntValue() - entry.getKey().decayPerDay;
                if (amount < DISCARD_THRESHOLD) {
                    iterator.remove();
                } else {
                    entry.setValue(amount);
                }
            }
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public void makeSureValueIsntTooLow(GossipType type) {
            int amount = entries.getInt(type);
            if (amount < DISCARD_THRESHOLD) {
                remove(type);
            }
        }

        public void remove(GossipType type) {
            entries.removeInt(type);
        }
    }

    public record GossipEntry(UUID target, GossipType type, int value) {

        public int weightedValue() {
            return value * type.weight;
        }
    }
}