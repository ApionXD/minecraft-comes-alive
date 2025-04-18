package net.mca.item;

import net.mca.SoundsMCA;
import net.mca.TagsMCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.block.TombstoneBlock;
import net.mca.entity.EntitiesMCA;
import net.mca.util.localization.FlowingText;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScytheItem extends SwordItem {

    public ScytheItem(Settings settings) {
        super(ToolMaterials.GOLD, 10, -2.4F, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.addAll(FlowingText.wrap(Text.translatable(getTranslationKey(stack) + ".tooltip").formatted(Formatting.GRAY), 160));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        boolean active = stack.getOrCreateNbt().getBoolean("active");

        Random r = entity.world.random;

        if (active != selected) {
            stack.getOrCreateNbt().putBoolean("active", selected);

            float baseVolume = selected ? 0.75F : 0.25F;
            entity.world.playSound(null, entity.getBlockPos(), SoundsMCA.REAPER_SCYTHE_OUT.get(), entity.getSoundCategory(),
                    baseVolume + r.nextFloat() / 2F,
                    0.65F + r.nextFloat() / 10F
            );
        }

        if (selected) {
            if (living.handSwingTicks == -1) {
                entity.world.playSound(null, entity.getBlockPos(), SoundsMCA.REAPER_SCYTHE_SWING.get(), entity.getSoundCategory(), 0.25F, 1);
            }
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return super.use(world, user, hand);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (hasSoul(context.getStack())) {
            ActionResult result = use(context, false);
            if (result == ActionResult.SUCCESS) {
                setSoul(context.getStack(), false);
            }
            if (result != ActionResult.PASS) {
                return result;
            }
        }

        return super.useOnBlock(context);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return super.hasGlint(stack) || hasSoul(stack);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.world.random.nextInt(50) > 40) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 1000, 1));
        }

        SoundEvent sound = SoundsMCA.REAPER_SCYTHE_OUT.get();

        if (!hasSoul(stack) && target.isDead() && (target.getType() == EntitiesMCA.MALE_VILLAGER.get() || target.getType() == EntitiesMCA.FEMALE_VILLAGER.get())) {
            setSoul(stack, true);
            sound = SoundEvents.BLOCK_BELL_RESONATE;

            if (attacker instanceof ServerPlayerEntity) {
                CriterionMCA.GENERIC_EVENT_CRITERION.trigger((ServerPlayerEntity)attacker, "scytheKill");
            }
        }

        Random r = attacker.world.random;
        attacker.world.playSound(null, attacker.getBlockPos(), sound, attacker.getSoundCategory(),
                0.75F + r.nextFloat() / 2F,
                0.75F + r.nextFloat() / 2F
        );

        return super.postHit(stack, target, attacker);
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return stack.getItem() == ingredient.getItem();
    }

    public static void setSoul(ItemStack stack, boolean soul) {
        stack.getOrCreateNbt().putBoolean("hasSoul", soul);
    }

    public static boolean hasSoul(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().getBoolean("hasSoul");
    }

    public static ActionResult use(ItemUsageContext context, boolean cure) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);

        if (state.isIn(TagsMCA.Blocks.TOMBSTONES)) {
            return TombstoneBlock.Data.of(world.getBlockEntity(pos)).filter(TombstoneBlock.Data::hasEntity).map(data -> {
                if (!context.getWorld().isClient) {
                    CriterionMCA.GENERIC_EVENT_CRITERION.trigger((ServerPlayerEntity)context.getPlayer(), cure ? "staffOfLife" : "scytheRevive");
                }

                if (!world.isClient && !data.isResurrecting()) {
                    data.startResurrecting(cure);
                    return ActionResult.SUCCESS;
                }

                return ActionResult.PASS;
            }).orElse(ActionResult.FAIL);
        }
        return ActionResult.PASS;
    }
}
