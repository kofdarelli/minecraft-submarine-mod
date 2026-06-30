package com.submarine.protection;

import com.submarine.data.SubmarineMetadata;
import com.submarine.data.SubmarineSavedData;
import com.submarine.item.ModItems;
import com.submarine.seat.SubmarineSeatManager;
import com.submarine.template.SubmarineTemplate;
import com.submarine.template.SubmarineTemplates;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class SubmarineProtection {
    private SubmarineProtection() {
    }

    public static void registerEvents() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            Optional<SubmarineMetadata> metadata = metadataAt(level, hitResult.getBlockPos());
            if (metadata.isEmpty()) {
                return InteractionResult.PASS;
            }

            InteractionResult seatResult = SubmarineSeatManager.tryUseSeat(serverPlayer, level, metadata.get(), hitResult.getBlockPos());
            if (seatResult != InteractionResult.PASS) {
                return seatResult;
            }

            ItemStack stack = player.getItemInHand(hand);
            if (isBlockedFluidUse(stack) || isBlockedBlock(stack)) {
                player.displayClientMessage(Component.translatable("message.submarine.place_blocked"), true);
                return InteractionResult.FAIL;
            }
            if (stack.getItem() instanceof BlockItem) {
                BlockPlaceContext placeContext = new BlockPlaceContext(new UseOnContext(player, hand, hitResult));
                if (!canPlace(level, placeContext.getClickedPos(), metadata.get())) {
                    player.displayClientMessage(Component.translatable("message.submarine.place_blocked"), true);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) {
                return true;
            }
            Optional<SubmarineMetadata> metadata = metadataAt(level, pos);
            if (metadata.isEmpty()) {
                return true;
            }
            if (!canBreak(pos, metadata.get())) {
                player.displayClientMessage(Component.translatable("message.submarine.break_blocked"), true);
                return false;
            }
            return true;
        });
    }

    public static Optional<SubmarineMetadata> metadataAt(ServerLevel level, BlockPos pos) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            return Optional.empty();
        }
        return SubmarineSavedData.get(level).get(ship.getId());
    }

    private static boolean canBreak(BlockPos shipyardPos, SubmarineMetadata metadata) {
        SubmarineTemplate template = SubmarineTemplates.get(metadata.templateId());
        BlockPos local = metadata.toLocal(shipyardPos);
        return template.isEditable(local)
                && !template.protectedLocalPositions().contains(local.asLong());
    }

    private static boolean canPlace(ServerLevel level, BlockPos shipyardPos, SubmarineMetadata metadata) {
        SubmarineTemplate template = SubmarineTemplates.get(metadata.templateId());
        BlockPos local = metadata.toLocal(shipyardPos);
        if (!template.isEditable(local)) {
            return false;
        }
        if (template.protectedLocalPositions().contains(local.asLong())) {
            return false;
        }
        BlockState existing = level.getBlockState(shipyardPos);
        return existing.isAir() || existing.canBeReplaced();
    }

    private static boolean isBlockedFluidUse(ItemStack stack) {
        return stack.getItem() instanceof BucketItem
                && stack.getItem() != Items.BUCKET
                && stack.getItem() != Items.MILK_BUCKET;
    }

    private static boolean isBlockedBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock() == Blocks.TNT
                || blockItem.getBlock() == Blocks.NETHER_PORTAL
                || blockItem.getBlock() == Blocks.END_PORTAL
                || stack.getItem() == ModItems.SUBMARINE_BLUEPRINT;
    }
}
