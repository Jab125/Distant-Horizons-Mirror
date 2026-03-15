package com.seibel.distanthorizons.cleanroom.mixins.server;

import com.seibel.distanthorizons.common.wrappers.misc.IMixinServerPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP implements IMixinServerPlayer
{
	@Shadow 
	@Final
	public MinecraftServer server;
	
	@Unique
	@Nullable
	private volatile WorldServer distantHorizons$dimensionChangeDestination;
	
	@Override
	@Nullable
	public WorldServer distantHorizons$getDimensionChangeDestination()
	{
		return this.distantHorizons$dimensionChangeDestination;
	}
	
	@Inject(at = @At("HEAD"), method = "changeDimension(ILnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/entity/Entity;")
	public void setDimensionChangeDestination(int destinationDimensionID, ITeleporter teleporter, CallbackInfoReturnable<Entity> cir)
	{
		this.distantHorizons$dimensionChangeDestination = this.server.getWorld(destinationDimensionID);
	}
	
	@Inject(at = @At("RETURN"), method = "clearInvulnerableDimensionChange")
	public void clearDimensionChangeDestination(CallbackInfo ci)
	{
		this.distantHorizons$dimensionChangeDestination = null;
	}
	
}
