package com.submarine.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.valkyrienskies.core.api.ships.ServerShip;

public final class SubmarineBlueprintItem extends Item {
    private final SubmarineFactory submarineFactory;

    public SubmarineBlueprintItem(Properties properties, SubmarineFactory submarineFactory) {
        super(properties);
        this.submarineFactory = submarineFactory;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        spawnAndConsume(level, player, context.getItemInHand(), context.getClickedPos().relative(context.getClickedFace()));
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }

        spawnAndConsume(serverLevel, player, stack, hit.getBlockPos());
        return InteractionResultHolder.consume(stack);
    }

    private void spawnAndConsume(ServerLevel level, Player player, ItemStack stack, BlockPos origin) {
        submarineFactory.spawn(level, player, origin);
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    @FunctionalInterface
    public interface SubmarineFactory {
        ServerShip spawn(ServerLevel level, Player owner, BlockPos requestedOrigin);
    }
}
