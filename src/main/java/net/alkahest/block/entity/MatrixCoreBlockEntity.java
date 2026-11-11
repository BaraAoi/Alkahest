package net.alkahest.block.entity;

import net.alkahest.block.custom.AlchemicalBrickBlock;
import net.alkahest.block.custom.MatrixCoreBlock;
import net.alkahest.block.custom.MatrixPillarBlock;
import net.alkahest.effect.MidasTouchEffect;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.matrix.MatrixRecipe;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Centre of the 3x3x3 Matrix. The base item sits inside the core and the extra parts lie on the
 * pedestals around the cube; essence is drawn out of the jars through the air. A freshly built
 * cube is dormant — a wand has to wake it up before any ring starts to turn.
 */
public class MatrixCoreBlockEntity extends BlockEntity implements Inventory {
    public static final int DURATION = 160;
    public static final int BUFFER = 128;
    public static final int REACH = 12;
    public static final int PEDESTAL_REACH = 8;
    public static final int PEDESTAL_HEIGHT = 3;
    public static final int BASE = 0, OUTPUT = 1;

    public static final int STATUS_IDLE = 0, STATUS_WORKING = 1, STATUS_NO_ESSENCE = 2,
        STATUS_BLOCKED = 3, STATUS_UNFORMED = 4, STATUS_NO_RECIPE = 5, STATUS_ASLEEP = 6, STATUS_DONE = 7,
        STATUS_NEEDS_START = 8, STATUS_AMBIGUOUS = 9;

    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(2, ItemStack.EMPTY);
    private JarContentsComponent buffer = JarContentsComponent.EMPTY;
    private int progress;
    private int atmosphereCredit;
    private boolean awake;
    private boolean started;
    private String startedRecipeKey = "";

    /** Essence streams that are still crossing the air; purely a visual, never saved. */
    private final List<EssenceFlight> flights = new ArrayList<>();
    private final List<ItemFlight> itemFlights = new ArrayList<>();

    public MatrixCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATRIX_CORE, pos, state);
    }

    public int progress() { return progress; }
    public JarContentsComponent buffer() { return buffer; }
    public boolean awake() { return awake; }
    public boolean started() { return started; }

    /** Eight pillars on the corners and eighteen bricks on the rest of the 3x3x3 shell. */
    public boolean formed() {
        if (world == null) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block block = world.getBlockState(pos.add(dx, dy, dz)).getBlock();
                    boolean corner = dx != 0 && dy != 0 && dz != 0;
                    if (corner && !(block instanceof MatrixPillarBlock)) return false;
                    if (!corner && !(block instanceof AlchemicalBrickBlock)) return false;
                }
            }
        }
        return true;
    }

    /** Every pedestal standing around the cube. */
    public List<MatrixPedestalBlockEntity> pedestals() {
        List<MatrixPedestalBlockEntity> result = new ArrayList<>();
        if (world == null) return result;
        for (BlockPos candidate : BlockPos.iterate(pos.add(-PEDESTAL_REACH, -PEDESTAL_HEIGHT, -PEDESTAL_REACH),
            pos.add(PEDESTAL_REACH, PEDESTAL_HEIGHT, PEDESTAL_REACH))) {
            int dx = candidate.getX() - pos.getX(), dz = candidate.getZ() - pos.getZ();
            if (dx * dx + dz * dz > PEDESTAL_REACH * PEDESTAL_REACH) continue;
            if (world.getBlockEntity(candidate) instanceof MatrixPedestalBlockEntity pedestal) result.add(pedestal);
        }
        return result;
    }

    public List<ItemStack> pedestalItems() {
        List<ItemStack> result = new ArrayList<>();
        for (MatrixPedestalBlockEntity pedestal : pedestals()) {
            if (!pedestal.isEmpty()) result.add(pedestal.held());
        }
        return result;
    }

    public MatrixRecipe recipe() {
        if (world == null || stacks.get(BASE).isEmpty()) return null;
        MatrixRecipe found = MatrixRecipe.find(stacks.get(BASE), pedestalItems());
        if (started && (found == null || !found.key().equals(startedRecipeKey))) return null;
        return found;
    }

    public int status() {
        if (!formed()) return STATUS_UNFORMED;
        if (!awake) return STATUS_ASLEEP;
        if (stacks.get(BASE).isEmpty() && !stacks.get(OUTPUT).isEmpty()) return STATUS_DONE;
        MatrixRecipe recipe = recipe();
        if (recipe == null) {
            if (stacks.get(BASE).isEmpty()) return STATUS_IDLE;
            return MatrixRecipe.matches(stacks.get(BASE), pedestalItems()).size() > 1 ? STATUS_AMBIGUOUS : STATUS_NO_RECIPE;
        }
        if (!canStoreOutput(recipe.output())) return STATUS_BLOCKED;
        if (!started) return STATUS_NEEDS_START;
        if (progress >= DURATION) return STATUS_NO_ESSENCE;
        return STATUS_WORKING;
    }

    private boolean canStoreOutput(ItemStack product) {
        if (product.isEmpty()) return false;
        ItemStack output = stacks.get(OUTPUT);
        if (output.isEmpty()) return true;
        if (!ItemStack.areItemsEqual(output, product)) return false;
        return output.getCount() + product.getCount() <= output.getMaxCount();
    }

    /**
     * Wakes the cube once and for all: the shell blocks stay in place but turn invisible, so the
     * finished halo of the core is the only thing the player sees.
     */
    public void setAwake(World world, BlockPos pos, boolean value) {
        if (awake == value) return;
        awake = value;
        if (!value) { progress = 0; started = false; startedRecipeKey = ""; }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof MatrixCoreBlock && state.get(MatrixCoreBlock.AWAKE) != value) {
            world.setBlockState(pos, state.with(MatrixCoreBlock.AWAKE, value), Block.NOTIFY_ALL);
        }
        revealShell(world, pos, value);
        if (value) {
            wakeEffect(world, pos);
        } else {
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 0.7f, 0.8f);
        }
        markDirty();
        sync();
    }

    /** Hides or restores the twenty-six blocks of the cube without removing them. */
    private static void revealShell(World world, BlockPos pos, boolean revealed) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos target = pos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(target);
                    if (state.getBlock() instanceof MatrixPillarBlock) {
                        if (state.get(MatrixPillarBlock.REVEALED) != revealed)
                            world.setBlockState(target, state.with(MatrixPillarBlock.REVEALED, revealed), Block.NOTIFY_ALL);
                    } else if (state.getBlock() instanceof AlchemicalBrickBlock) {
                        if (state.get(AlchemicalBrickBlock.REVEALED) != revealed)
                            world.setBlockState(target, state.with(AlchemicalBrickBlock.REVEALED, revealed), Block.NOTIFY_ALL);
                    }
                }
            }
        }
    }

    public static void tick(World world, BlockPos pos, MatrixCoreBlockEntity self) {
        if (world.isClient) return;
        if (world instanceof ServerWorld server) {
            self.advanceFlights(server);
            self.advanceItemFlights(server);
        }
        if (!self.formed()) {
            if (self.awake) self.setAwake(world, pos, false);
            if (self.progress != 0) { self.progress = 0; self.markDirty(); }
            setActive(world, pos, self, false);
            return;
        }
        MatrixRecipe recipe = self.awake ? self.recipe() : null;
        if (recipe == null || !self.canStoreOutput(recipe.output())) {
            if (self.started || self.progress != 0) {
                self.started = false;
                self.startedRecipeKey = "";
                self.progress = 0;
                self.markDirty();
                self.sync();
            }
            setActive(world, pos, self, false);
            return;
        }
        if (!self.started) {
            setActive(world, pos, self, false);
            return;
        }
        if (self.progress < DURATION) {
            int before = self.progress;
            int rate = 100;
            if (world instanceof ServerWorld server)
                rate = net.alkahest.atmosphere.AtmosphereMath.stability(recipe.cost(),
                    net.alkahest.atmosphere.AtmosphereState.of(server).levels(server, pos));
            self.atmosphereCredit += rate * MidasTouchEffect.progressStep(MidasTouchEffect.nearby(world, pos), world.getTime());
            int increment = self.atmosphereCredit / 100;
            self.atmosphereCredit %= 100;
            self.progress = Math.min(DURATION, before + increment);
            // Boosted steps can jump over a multiple of ten; never skip an essence request.
            if (before / 10 != self.progress / 10) {
                self.prefill(world, recipe.cost());
                self.sync();
            }
            setActive(world, pos, self, true);
            self.markDirty();
            return;
        }
        if (!self.pay(world, recipe.cost())) {
            setActive(world, pos, self, false);
            return;
        }
        self.consumeItems(recipe);
        net.alkahest.atmosphere.AtmosphereEvents.emit(world, pos, recipe.cost(), 0.1);
        self.atmosphereCredit = 0;
        self.started = false;
        self.startedRecipeKey = "";
        self.progress = 0;
        self.markDirty();
        self.sync();
        celebrate(world, pos);
    }

    /** Start one synthesis only. Bad recipes or a depleted amulet do not consume a use. */
    public boolean startWith(ItemStack focus, PlayerEntity player) {
        if (world == null || world.isClient || started || !awake || !formed()) return false;
        MatrixRecipe recipe = recipe();
        if (recipe == null || !canStoreOutput(recipe.output())) return false;
        if (focus.isOf(ModItems.ALKAHEST_AMULET)) {
            AlkahestAmuletItem amulet = (AlkahestAmuletItem) focus.getItem();
            if (!amulet.consumeUseStack(player, focus)) return false;
        } else if (!focus.isOf(ModItems.PHILOSOPHERS_STONE)) return false;
        startedRecipeKey = recipe.key();
        started = true;
        progress = 0;
        atmosphereCredit = 0;
        if (world instanceof ServerWorld server) {
            int stability = net.alkahest.atmosphere.AtmosphereMath.stability(recipe.cost(),
                net.alkahest.atmosphere.AtmosphereState.of(server).levels(server, pos));
            player.sendMessage(Text.translatable("message.alkahest.matrix_air_stability", stability), true);
        }
        markDirty();
        sync();
        world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.7f, 1.4f);
        return true;
    }

    /** Find the nearest formed Matrix that can actually reach this pedestal. */
    public static MatrixCoreBlockEntity forPedestal(World world, BlockPos pedestal) {
        MatrixCoreBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.iterate(pedestal.add(-PEDESTAL_REACH, -PEDESTAL_HEIGHT, -PEDESTAL_REACH),
            pedestal.add(PEDESTAL_REACH, PEDESTAL_HEIGHT, PEDESTAL_REACH))) {
            int dx = candidate.getX() - pedestal.getX(), dz = candidate.getZ() - pedestal.getZ();
            if (dx * dx + dz * dz > PEDESTAL_REACH * PEDESTAL_REACH) continue;
            if (world.getBlockEntity(candidate) instanceof MatrixCoreBlockEntity core && core.formed()) {
                double distance = candidate.getSquaredDistance(pedestal);
                if (distance < best) { nearest = core; best = distance; }
            }
        }
        return nearest;
    }

    /** Spends the base item and one copy of every part lying on the pedestals. */
    private void consumeItems(MatrixRecipe recipe) {
        ItemStack base = stacks.get(BASE);
        base.decrement(1);
        if (base.isEmpty()) stacks.set(BASE, ItemStack.EMPTY);
        for (net.minecraft.item.Item component : recipe.components()) {
            for (MatrixPedestalBlockEntity pedestal : pedestals()) {
                if (pedestal.isEmpty()) continue;
                if (pedestal.held().getItem() != component) continue;
                ItemStack absorbed = pedestal.takeOne();
                launchItemFlight(pedestal.getPos(), absorbed);
                break;
            }
        }
        ItemStack product = recipe.output().copy();
        ItemStack stored = stacks.get(OUTPUT);
        if (stored.isEmpty()) stacks.set(OUTPUT, product);
        else stored.increment(product.getCount());
    }

    /** Draws one unit of every still-missing essence, so sparks fly for the whole operation. */
    private void prefill(World world, Map<EssenceType, Integer> cost) {
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            EssenceType type = entry.getKey();
            if (stored(type) >= entry.getValue()) continue;
            request(world, type, 1);
        }
    }

    /** Pays the whole price at once; nothing is taken when it cannot be paid. */
    private boolean pay(World world, Map<EssenceType, Integer> cost) {
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            int need = entry.getValue();
            if (stored(entry.getKey()) + jarsAround(world, entry.getKey()) < need) return false;
        }
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            int need = entry.getValue();
            while (need > 0 && stored(entry.getKey()) < need) {
                int pulled = request(world, entry.getKey(), need - stored(entry.getKey()));
                if (pulled <= 0) break;
            }
            if (stored(entry.getKey()) < need) return false;
            take(entry.getKey(), need);
        }
        markDirty();
        return true;
    }

    public int stored(EssenceType type) {
        for (EssenceStack stack : buffer.contents()) if (stack.type() == type) return stack.amount();
        return 0;
    }

    /** Pulls up to {@code count} units out of the nearest matching jar with a visible trail. */
    public int request(World world, EssenceType type, int count) {
        int room = BUFFER - buffer.total();
        if (room <= 0) return 0;
        JarBlockEntity jar = findJar(world, type);
        if (jar == null) return 0;
        EssenceStack source = jar.contents().contents().getFirst();
        int want = Math.min(Math.min(count, room), source.amount());
        if (want <= 0) return 0;
        List<EssenceStack> remainder = new ArrayList<>(jar.contents().contents());
        remainder.remove(source);
        if (source.amount() > want) {
            remainder.add(new EssenceStack(source.type(), source.amount() - want, source.stability()));
        }
        jar.setContents(new JarContentsComponent(remainder));
        buffer = buffer.merge(new JarContentsComponent(
            List.of(new EssenceStack(type, want, source.stability()))), BUFFER);
        launchFlight(world, jar.getPos(), type, want);
        markDirty();
        return want;
    }

    /** Removes up to {@code count} units of the type from the core buffer. */
    public int take(EssenceType type, int count) {
        int removed = 0;
        List<EssenceStack> remainder = new ArrayList<>();
        for (EssenceStack stack : buffer.contents()) {
            if (stack.type() != type || removed >= count) { remainder.add(stack); continue; }
            int taken = Math.min(count - removed, stack.amount());
            removed += taken;
            if (stack.amount() > taken) {
                remainder.add(new EssenceStack(type, stack.amount() - taken, stack.stability()));
            }
        }
        if (removed == 0) return 0;
        buffer = new JarContentsComponent(remainder);
        markDirty();
        return removed;
    }

    private int jarsAround(World world, EssenceType type) {
        int total = 0;
        for (BlockPos candidate : BlockPos.iterate(pos.add(-REACH, -REACH, -REACH), pos.add(REACH, REACH, REACH))) {
            if (!(world.getBlockEntity(candidate) instanceof JarBlockEntity jar)) continue;
            JarContentsComponent contents = jar.contents();
            if (contents.contents().size() != 1) continue;
            if (contents.contents().getFirst().type() != type) continue;
            total += contents.total();
        }
        return total;
    }

    private JarBlockEntity findJar(World world, EssenceType type) {
        JarBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.iterate(pos.add(-REACH, -REACH, -REACH), pos.add(REACH, REACH, REACH))) {
            if (!(world.getBlockEntity(candidate) instanceof JarBlockEntity jar)) continue;
            JarContentsComponent contents = jar.contents();
            if (contents.total() == 0 || contents.contents().size() != 1) continue;
            if (contents.contents().getFirst().type() != type) continue;
            double distance = candidate.getSquaredDistance(pos);
            if (distance < bestDistance) { bestDistance = distance; best = jar; }
        }
        return best;
    }

    /** Short visual-only stream of fragments from a consumed pedestal item to the core. */
    private record ItemFlight(BlockPos from, ItemStack item, long start) { }
    private static final int ITEM_FLIGHT_TICKS = 28;

    private void launchItemFlight(BlockPos from, ItemStack item) {
        if (world != null && !item.isEmpty())
            itemFlights.add(new ItemFlight(from.toImmutable(), item.copyWithCount(1), world.getTime()));
    }

    private void advanceItemFlights(ServerWorld server) {
        Iterator<ItemFlight> iterator = itemFlights.iterator();
        while (iterator.hasNext()) {
            ItemFlight flight = iterator.next();
            int age = (int) (server.getTime() - flight.start());
            if (age < 0 || age > ITEM_FLIGHT_TICKS) { iterator.remove(); continue; }
            double t = age / (double) ITEM_FLIGHT_TICKS;
            double x = flight.from().getX() + 0.5 + (pos.getX() - flight.from().getX()) * t;
            double y = flight.from().getY() + 1.15 + (pos.getY() - flight.from().getY() - 0.1) * t
                + Math.sin(t * Math.PI) * 0.5;
            double z = flight.from().getZ() + 0.5 + (pos.getZ() - flight.from().getZ()) * t;
            server.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, flight.item()),
                x, y, z, 2, 0.04, 0.04, 0.04, 0.005);
            server.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.55f, 1f), 1.0f),
                x, y, z, 2, 0.06, 0.06, 0.06, 0.005);
        }
    }

    /** Ticks one unit of essence spends leaving the jar, crossing the air and soaking into the core. */
    public static final int FLIGHT_TICKS = 52;
    private static final int FLIGHT_OOZE = 14;
    private static final int FLIGHT_SOAK = 12;

    /** A stream that is still on its way; purely visual, so it is never written to disk. */
    private record EssenceFlight(BlockPos from, EssenceType type, long start, int units) { }

    /** Queues a slow, clearly visible stream from the jar mouth to the item waiting in the core. */
    private void launchFlight(World world, BlockPos from, EssenceType type, int units) {
        if (flights.size() >= 20) flights.remove(0);
        flights.add(new EssenceFlight(from.toImmutable(), type, world.getTime(), units));
    }

    /**
     * Advances every stream by one tick. The essence does not simply appear on the item: it first
     * wells out of the jar as a fat liquid bead, then sails across the air as a comet, and only at
     * the very end spirals down and is drunk by the item in the core.
     */
    private void advanceFlights(ServerWorld server) {
        if (flights.isEmpty()) return;
        long now = server.getTime();
        Iterator<EssenceFlight> iterator = flights.iterator();
        while (iterator.hasNext()) {
            EssenceFlight flight = iterator.next();
            int age = (int) (now - flight.start());
            if (age < 0 || age > FLIGHT_TICKS) { iterator.remove(); continue; }
            emitFlight(server, flight, age);
        }
    }

    private void emitFlight(ServerWorld server, EssenceFlight flight, int age) {
        int rgb = flight.type().color();
        Vector3f tint = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        double x0 = flight.from().getX() + 0.5, y0 = flight.from().getY() + 1.02, z0 = flight.from().getZ() + 0.5;
        double x1 = pos.getX() + 0.5, y1 = pos.getY() + 1.05, z1 = pos.getZ() + 0.5;
        double distance = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));

        if (age <= FLIGHT_OOZE) {
            // Liquid wells up out of the jar: a bead that grows, wobbles and finally lets go.
            double swell = age / (double) FLIGHT_OOZE;
            DustParticleEffect bead = new DustParticleEffect(tint, 1.1f + (float) swell * 0.8f);
            server.spawnParticles(bead, x0, y0 + 0.05 + swell * 0.32, z0,
                3, 0.09 - swell * 0.05, 0.04, 0.09 - swell * 0.05, 0.0);
            if (age % 3 == 0) {
                server.spawnParticles(new DustParticleEffect(tint, 0.7f),
                    x0 + (server.random.nextDouble() - 0.5) * 0.22, y0 + 0.02,
                    z0 + (server.random.nextDouble() - 0.5) * 0.22, 1, 0.0, -0.02, 0.0, 0.01);
            }
            return;
        }

        int travelEnd = FLIGHT_TICKS - FLIGHT_SOAK;
        if (age < travelEnd) {
            double t = (age - FLIGHT_OOZE) / (double) (travelEnd - FLIGHT_OOZE);
            double rise = Math.sin(t * Math.PI) * (0.55 + distance * 0.06);
            double head = t * t * (3 - 2 * t); // ease in and out, so it does not jerk away from the jar
            double x = x0 + (x1 - x0) * head;
            double y = y0 + (y1 - y0) * head + rise;
            double z = z0 + (z1 - z0) * head;
            server.spawnParticles(new DustParticleEffect(tint, 1.35f), x, y, z, 2, 0.02, 0.02, 0.02, 0.0);
            // A short tail so the bead reads as a streak of liquid rather than a dot.
            double tail = Math.max(0.0, head - 0.06);
            if (age % 2 == 0) {
                server.spawnParticles(new DustParticleEffect(tint, 0.85f),
                    x0 + (x1 - x0) * tail, y0 + (y1 - y0) * tail + Math.sin(tail * Math.PI) * (0.55 + distance * 0.06),
                    z0 + (z1 - z0) * tail, 1, 0.015, 0.015, 0.015, 0.0);
            }
            return;
        }

        // Absorption: the essence circles the item once and is pulled into it.
        double soak = (age - travelEnd) / (double) FLIGHT_SOAK;
        double radius = 0.42 * (1 - soak);
        double angle = soak * Math.PI * 3;
        double x = x1 + Math.cos(angle) * radius;
        double z = z1 + Math.sin(angle) * radius;
        double y = y1 + 0.22 * (1 - soak);
        server.spawnParticles(new DustParticleEffect(tint, 1.2f - (float) soak * 0.7f), x, y, z,
            2, 0.01, 0.01, 0.01, 0.0);
        if (age == FLIGHT_TICKS) {
            server.spawnParticles(new DustParticleEffect(tint, 1.0f), x1, y1, z1, 8, 0.12, 0.12, 0.12, 0.02);
        }
    }

    /** Wakes the finished cube through a wand click on any of its blocks. */
    public Text statusText() {
        return Text.translatable("screen.alkahest.matrix." + switch (status()) {
            case STATUS_WORKING -> "working";
            case STATUS_NO_ESSENCE -> "no_essence";
            case STATUS_BLOCKED -> "blocked";
            case STATUS_UNFORMED -> "unformed";
            case STATUS_NO_RECIPE -> "no_recipe";
            case STATUS_ASLEEP -> "asleep";
            case STATUS_DONE -> "done";
            case STATUS_NEEDS_START -> "needs_start";
            case STATUS_AMBIGUOUS -> "ambiguous";
            default -> "ready";
        });
    }

    /** Puts the held item into the empty core, where it floats until the Matrix uses it. */
    public boolean insertBase(ItemStack stack, PlayerEntity player) {
        if (stack.isEmpty() || world == null) return false;
        if (!stacks.get(BASE).isEmpty()) return false;
        stacks.set(BASE, stack.copyWithCount(1));
        progress = 0;
        if (!player.isCreative()) stack.decrement(1);
        markDirty();
        sync();
        world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        if (world instanceof ServerWorld server) {
            server.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.55f, 1f), 0.9f),
                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.02);
        }
        return true;
    }

    /** Hands the finished product over, then the leftovers of the base, and finally just reports. */
    public boolean takeFromCore(PlayerEntity player) {
        if (world == null) return false;
        ItemStack product = stacks.get(OUTPUT);
        if (!product.isEmpty()) {
            stacks.set(OUTPUT, ItemStack.EMPTY);
            give(player, product);
            markDirty();
            sync();
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 0.6f, 1.5f);
            return true;
        }
        ItemStack base = stacks.get(BASE);
        if (!base.isEmpty()) {
            stacks.set(BASE, ItemStack.EMPTY);
            started = false;
            startedRecipeKey = "";
            progress = 0;
            give(player, base);
            markDirty();
            sync();
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 0.5f, 1.6f);
            return true;
        }
        return false;
    }

    private static void give(PlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
    }

    /** Pushes the core contents to the clients, so no ghost item is left floating in the altar. */
    private void sync() {
        if (!(world instanceof ServerWorld server)) return;
        BlockState state = world.getBlockState(pos);
        server.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        Packet<?> packet = toUpdatePacket();
        if (packet != null) {
            server.getServer().getPlayerManager().sendToAround(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                64.0, server.getRegistryKey(), packet);
        }
    }

    private static void celebrate(World world, BlockPos pos) {
        if (world instanceof ServerWorld server) {
            server.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.55f, 1f), 1.1f),
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 40, 1.2, 1.2, 1.2, 0.08);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1f, 0.6f);
        }
    }

    /** The awakening: rings unlock, pyramids take flight, the whole cube lights up. */
    private static void wakeEffect(World world, BlockPos pos) {
        if (world instanceof ServerWorld server) {
            server.spawnParticles(new DustParticleEffect(new Vector3f(0.9f, 0.7f, 1f), 1.2f),
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 60, 1.6, 1.6, 1.6, 0.12);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.9f, 1.2f);
    }

    /** Switches the glow of the whole shell, so a working Matrix lights up as one machine. */
    private static void setActive(World world, BlockPos pos, MatrixCoreBlockEntity self, boolean active) {
        if (self.active == active) return;
        self.active = active;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos target = pos.add(dx, dy, dz);
                    glow(world, target, world.getBlockState(target), active);
                }
            }
        }
        self.markDirty();
    }

    private static void glow(World world, BlockPos target, BlockState state, boolean value) {
        if (state.getBlock() instanceof MatrixCoreBlock) {
            if (state.get(MatrixCoreBlock.ACTIVE) != value)
                world.setBlockState(target, state.with(MatrixCoreBlock.ACTIVE, value), Block.NOTIFY_ALL);
        } else if (state.getBlock() instanceof MatrixPillarBlock) {
            if (state.get(MatrixPillarBlock.ACTIVE) != value)
                world.setBlockState(target, state.with(MatrixPillarBlock.ACTIVE, value), Block.NOTIFY_ALL);
        } else if (state.getBlock() instanceof AlchemicalBrickBlock) {
            if (state.get(AlchemicalBrickBlock.ACTIVE) != value)
                world.setBlockState(target, state.with(AlchemicalBrickBlock.ACTIVE, value), Block.NOTIFY_ALL);
        }
    }

    private boolean active;

    public boolean active() { return active; }

    // --- inventory --------------------------------------------------------------------------
    @Override public int size() { return 2; }
    @Override public boolean isEmpty() {
        for (ItemStack stack : stacks) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStack(int slot) { return stacks.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) {
            if (slot != OUTPUT) { progress = 0; started = false; startedRecipeKey = ""; }
            markDirty();
        }
        return removed;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) {
            if (slot != OUTPUT) { progress = 0; started = false; startedRecipeKey = ""; }
            markDirty();
        }
        return removed;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        if (slot != OUTPUT) { progress = 0; started = false; startedRecipeKey = ""; }
        markDirty();
    }
    @Override public void clear() { stacks.clear(); progress = 0; started = false; startedRecipeKey = ""; markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }
    @Override public boolean isValid(int slot, ItemStack stack) { return slot != OUTPUT; }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, stacks, registries);
        nbt.putInt("Progress", progress);
        nbt.putInt("AtmosphereCredit", atmosphereCredit);
        nbt.putBoolean("Awake", awake);
        nbt.putBoolean("Started", started);
        nbt.putString("StartedRecipe", started ? startedRecipeKey : "");
        nbt.putBoolean("Active", active);
        nbt.put("Buffer", JarContentsComponent.CODEC.encodeStart(NbtOps.INSTANCE, buffer)
            .result().orElseGet(NbtCompound::new));
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        // Inventory NBT omits empty slots; a client replica must discard its old values.
        stacks.clear();
        Inventories.readNbt(nbt, stacks, registries);
        progress = nbt.getInt("Progress");
        atmosphereCredit = Math.max(0, Math.min(99, nbt.getInt("AtmosphereCredit")));
        awake = nbt.getBoolean("Awake");
        started = nbt.getBoolean("Started");
        startedRecipeKey = nbt.getString("StartedRecipe");
        // Old running saves lack a recipe identity; stop safely rather than guessing a new recipe.
        if (!started || startedRecipeKey.isEmpty()) {
            started = false;
            startedRecipeKey = "";
            progress = 0;
        }
        active = nbt.getBoolean("Active");
        buffer = JarContentsComponent.CODEC.parse(NbtOps.INSTANCE, nbt.get("Buffer"))
            .result().orElse(JarContentsComponent.EMPTY);
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    public void dropContents(World world, BlockPos pos) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) net.minecraft.util.ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        stacks.clear();
        progress = 0;
        started = false;
        startedRecipeKey = "";
    }

    /**
     * One click, one big core: every block of the finished cube responds to the same gesture, so
     * the player never has to reach the block sitting in the middle of the multiblock. A wand wakes
     * the cube, a held item is put inside, an empty hand takes what is there.
     */
    public static void interactNearby(World world, BlockPos clicked, PlayerEntity player, ItemStack held) {
        if (world.isClient || player.isSneaking()) return;
        if (held.getItem() instanceof net.alkahest.item.custom.CrystalWandItem) {
            awakenNearby(world, clicked, player);
            return;
        }
        MatrixCoreBlockEntity core = coreNearby(world, clicked);
        if (core == null) return;
        if (held.isOf(ModItems.ALKAHEST_AMULET) || held.isOf(ModItems.PHILOSOPHERS_STONE)) {
            core.startWith(held, player);
            return;
        }
        if (!held.isEmpty()) core.insertBase(held, player);
        else core.takeFromCore(player);
    }

    /** The built Matrix that owns a clicked shell block, or null when the cube is not complete. */
    public static MatrixCoreBlockEntity coreNearby(World world, BlockPos clicked) {
        for (BlockPos candidate : BlockPos.iterate(clicked.add(-2, -2, -2), clicked.add(2, 2, 2))) {
            if (world.getBlockEntity(candidate) instanceof MatrixCoreBlockEntity core && core.formed()) return core;
        }
        return null;
    }

    /** Wakes the built Matrix next to a clicked shell block. The awakening happens only once. */
    public static boolean awakenNearby(World world, BlockPos clicked, PlayerEntity player) {
        if (world.isClient) return true;
        for (BlockPos candidate : BlockPos.iterate(clicked.add(-2, -2, -2), clicked.add(2, 2, 2))) {
            if (!(world.getBlockEntity(candidate) instanceof MatrixCoreBlockEntity core) || !core.formed()) continue;
            if (!core.awake()) core.setAwake(world, candidate, true);
            return true;
        }
        return false;
    }

}
