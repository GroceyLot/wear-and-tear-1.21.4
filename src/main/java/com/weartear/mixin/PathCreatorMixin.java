package com.weartear.mixin;

import com.weartear.config.WearTearConfig;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public abstract class PathCreatorMixin {

	@Unique
	private static final Set<Block> CONVERTIBLE_BLOCKS = Set.of(
			Blocks.GRASS_BLOCK,
			Blocks.DIRT,
			Blocks.COARSE_DIRT,
			Blocks.ROOTED_DIRT,
			Blocks.PODZOL,
			Blocks.MYCELIUM
	);

	// This map tracks how many times a player has stood on a given block
	@Unique
	private static final Map<BlockPos, Integer> BLOCK_STAND_COUNT = new HashMap<>();

	// Instead of a hardcoded threshold, use the config
	// (timeToConvert = how many times to stand on block before converting)
	// e.g. 3 by default
	@Unique
	private static int conversionThreshold() {
		return WearTearConfig.timeToConvert;
	}

	// Instead of a hardcoded interval, use the config (e.g. 1200 by default)
	@Unique
	private static int decrementInterval() {
		return WearTearConfig.timeBetweenDecrements;
	}

	// We'll keep a global tick counter to know when we should decrement
	@Unique
	private static int globalTickCounter = 0;

	// Store the player's last position to detect movement
	@Unique
	private BlockPos lastTickPos = null;

	@Inject(method = "tick()V", at = @At("TAIL"))
	private void onPlayerTick(CallbackInfo ci) {
		ServerPlayerEntity player = (ServerPlayerEntity)(Object) this;

		// Only operate on the server side
		if (!(player.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		if (world.isClient) {
			return;
		}

		// Increment our global tick counter
		globalTickCounter++;

		// Decrement logic every X ticks (from config)
		if (globalTickCounter >= decrementInterval()) {
			// Reset the counter
			globalTickCounter = 0;

			// Decrement the stand count for every tracked block by 1, not below 0
			BLOCK_STAND_COUNT.replaceAll((pos, count) -> Math.max(count - 1, 0));
		}

		// Get the player's current block position
		BlockPos currentPos = player.getBlockPos();

		// If lastTickPos is null (first time), initialize it
		if (lastTickPos == null) {
			lastTickPos = currentPos;
			return;
		}

		// Check if the player has moved since last tick
		boolean hasntMoved = currentPos.equals(lastTickPos);

		// Update lastTickPos for next tick
		lastTickPos = currentPos;

		// Only increment the counter if the player actually moved
		if (hasntMoved) {
			return;
		}

		// The block directly under the player
		BlockPos blockBelowPlayer = currentPos.down();
		Block blockBelow = world.getBlockState(blockBelowPlayer).getBlock();

		// If the block is convertible, increment its "stand count"
		if (CONVERTIBLE_BLOCKS.contains(blockBelow)) {
			int newCount = BLOCK_STAND_COUNT.getOrDefault(blockBelowPlayer, 0) + 1;
			BLOCK_STAND_COUNT.put(blockBelowPlayer.toImmutable(), newCount);

			// Check if we've hit the config threshold for conversion
			if (newCount >= conversionThreshold()) {
				// Convert to dirt path
				world.setBlockState(blockBelowPlayer, Blocks.DIRT_PATH.getDefaultState());
				// Remove from the map so we start fresh if it ever converts back
				BLOCK_STAND_COUNT.remove(blockBelowPlayer);
			}
		}
	}
}
