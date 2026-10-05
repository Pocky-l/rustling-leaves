package com.pockyl.rustling_leaves.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.pockyl.rustling_leaves.LeafEffects;

import java.util.List;

/**
 * Hold right click to blow a cone of air out of the nozzle: it sweeps leaf litter ahead (clearing paths, piling up
 * drifts), throws loose leaves into the air and nudges items and creatures in its way.
 */
public final class LeafBlowerItem extends Item {
    public static final double RANGE = 7.0;
    /** Half-angle of the air cone, as its tangent (about 25 degrees). */
    public static final double SPREAD = 0.47;
    private static final double PUSH = 0.07;

    public LeafBlowerItem(Properties properties) {
        super(properties);
    }

    /** Where the air comes out: in front of the eyes, a little lower, like a tool held at the hip. */
    public static Vec3 nozzle(LivingEntity user) {
        return user.getEyePosition().add(user.getLookAngle().scale(0.8)).add(0.0, -0.35, 0.0);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        Vec3 nozzle = nozzle(user);
        Vec3 direction = user.getLookAngle();
        if (level.isClientSide) {
            LeafEffects.get().blow(user, nozzle, direction);
            return;
        }
        if (remainingUseDuration % 10 == 0) {
            level.playSound(null, nozzle.x, nozzle.y, nozzle.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.5F,
                    1.4F + level.random.nextFloat() * 0.2F);
        }
        AABB area = new AABB(nozzle, nozzle.add(direction.scale(RANGE))).inflate(RANGE * SPREAD);
        for (Entity entity : level.getEntities(user, area, LeafBlowerItem::pushable)) {
            Vec3 offset = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0).subtract(nozzle);
            double along = offset.dot(direction);
            if (along < 0.0 || along > RANGE) {
                continue;
            }
            double across = offset.subtract(direction.scale(along)).length();
            double radius = 0.3 + along * SPREAD;
            if (across > radius) {
                continue;
            }
            double strength = PUSH * (1.0 - along / RANGE) * (1.0 - across / radius);
            entity.push(direction.x * strength, direction.y * strength + strength * 0.2, direction.z * strength);
            entity.hurtMarked = true;
        }
    }

    private static boolean pushable(Entity entity) {
        return entity instanceof ItemEntity || entity instanceof LivingEntity || entity instanceof Projectile;
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rustling_leaves.leaf_blower.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
