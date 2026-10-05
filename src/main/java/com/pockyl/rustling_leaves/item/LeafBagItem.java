package com.pockyl.rustling_leaves.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.pockyl.rustling_leaves.LeafEffects;
import com.pockyl.rustling_leaves.registry.ModDataComponents;

import java.util.List;

/**
 * Hold right click to suck leaves off the ground into the bag; sneak and hold right click to pour them out again.
 * A full enough bag emptied into a composter turns leaves into compost.
 *
 * <p>The leaves on the ground exist only on each client, so the client of the player holding the bag reports how
 * many it collected ({@code BagCollectPayload}); pouring is decided here on the server and every client draws it.
 */
public final class LeafBagItem extends Item {
    public static final int POUR_PER_TICK = 6;
    /** Leaves that make one layer of compost. */
    public static final int COMPOST_COST = 32;

    public LeafBagItem(Properties properties) {
        super(properties);
    }

    public static BagContents contents(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BAG_CONTENTS.get(), BagContents.EMPTY);
    }

    /** Where leaves go in and come out: held in front of the player, at chest height. */
    public static Vec3 mouth(LivingEntity user) {
        return user.getEyePosition().add(user.getLookAngle().scale(0.7)).add(0.0, -0.45, 0.0);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        Vec3 mouth = mouth(user);
        Vec3 direction = user.getLookAngle();
        BagContents contents = contents(stack);
        if (user.isShiftKeyDown()) {
            if (contents.count() == 0) {
                return;
            }
            int poured = Math.min(POUR_PER_TICK, contents.count());
            if (level.isClientSide) {
                LeafEffects.get().pour(user, mouth, direction, poured, contents);
            } else {
                stack.set(ModDataComponents.BAG_CONTENTS.get(), contents.remove(poured));
                if (remainingUseDuration % 4 == 0) {
                    level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.AZALEA_LEAVES_STEP, SoundSource.PLAYERS, 0.6F,
                            1.1F + level.random.nextFloat() * 0.3F);
                }
            }
            return;
        }
        if (level.isClientSide) {
            LeafEffects.get().vacuum(user, mouth, direction);
        } else if (remainingUseDuration % 8 == 0) {
            level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.35F,
                    1.6F + level.random.nextFloat() * 0.2F);
        }
    }

    /** A bag emptied into a composter: one compost layer per {@link #COMPOST_COST} leaves. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();
        if (!state.is(Blocks.COMPOSTER) || contents(stack).count() < COMPOST_COST || state.getValue(ComposterBlock.LEVEL) >= 7) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            compost(serverLevel, pos, stack);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Fills the composter as far as the leaves in the bag go. Returns the layers added. */
    public static int compost(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        BagContents contents = contents(stack);
        int layer = state.getValue(ComposterBlock.LEVEL);
        int layers = Math.min(7 - layer, contents.count() / COMPOST_COST);
        if (layers <= 0) {
            return 0;
        }
        int newLayer = layer + layers;
        level.setBlock(pos, state.setValue(ComposterBlock.LEVEL, newLayer), 3);
        if (newLayer == 7) {
            level.scheduleTick(pos, state.getBlock(), 20);
        }
        stack.set(ModDataComponents.BAG_CONTENTS.get(), contents.remove(layers * COMPOST_COST));
        level.levelEvent(1500, pos, 1);
        level.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 1.0F, 1.0F);
        return layers;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return contents(stack).count() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * contents(stack).count() / BagContents.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.color(0.86F, 0.55F, 0.2F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rustling_leaves.leaf_bag.contents", contents(stack).count(), BagContents.CAPACITY)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.rustling_leaves.leaf_bag.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
