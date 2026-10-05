package com.pockyl.rustling_leaves.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.pockyl.rustling_leaves.network.WindSpellPayload;

import java.util.List;

/**
 * Right click a spot to raise a leaf whirlwind there; sneak + right click to send a squall rolling the way you look.
 * The wind lives on the clients, so the server tells everyone nearby (and the caster) where it happened.
 */
public final class WindStaffItem extends Item {
    private static final double REACH = 32.0;
    private static final int WHIRLWIND_COOLDOWN = 20 * 8;
    private static final int SQUALL_COOLDOWN = 20 * 12;

    public WindStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean squall = player.isShiftKeyDown();
        if (!level.isClientSide) {
            Vec3 look = player.getLookAngle();
            WindSpellPayload payload;
            if (squall) {
                Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
                payload = new WindSpellPayload(WindSpellPayload.SQUALL, player.getX(), player.getY(), player.getZ(), (float) flat.x,
                        (float) flat.z);
            } else {
                HitResult hit = player.pick(REACH, 1.0F, false);
                Vec3 at = hit.getType() == HitResult.Type.MISS ? player.getEyePosition().add(look.scale(12.0)) : hit.getLocation();
                payload = new WindSpellPayload(WindSpellPayload.WHIRLWIND, at.x, at.y, at.z, 0.0F, 0.0F);
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), squall ? SoundEvents.BREEZE_SHOOT : SoundEvents.WIND_CHARGE_THROW,
                    SoundSource.PLAYERS, 1.0F, squall ? 0.7F : 1.0F);
        }
        player.getCooldowns().addCooldown(this, squall ? SQUALL_COOLDOWN : WHIRLWIND_COOLDOWN);
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rustling_leaves.wind_staff.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rustling_leaves.wind_staff.tooltip_squall").withStyle(ChatFormatting.GRAY));
    }
}
