package com.icraft.entity;

import com.icraft.init.ModEntityTypes;
import com.icraft.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.phys.AABB;

public class PhotoFrameEntity extends HangingEntity {

    private static final EntityDataAccessor<String> FILENAME =
            SynchedEntityData.defineId(PhotoFrameEntity.class, EntityDataSerializers.STRING);

    public PhotoFrameEntity(EntityType<? extends PhotoFrameEntity> type, Level level) {
        super(type, level);
    }

    public PhotoFrameEntity(Level level, BlockPos pos, Direction dir, String filename) {
        super(ModEntityTypes.PHOTO_FRAME.get(), level, pos);
        this.setDirection(dir);
        this.entityData.set(FILENAME, filename);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FILENAME, "");
    }

    public String getFilename() {
        return entityData.get(FILENAME);
    }

    @Override
    public AABB calculateBoundingBox(BlockPos pos, Direction dir) {
        final double EPS = 1.0 / 32.0;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double hr = 0.5;

        return switch (dir) {
            case NORTH -> new AABB(cx - hr, cy - hr, cz - EPS, cx + hr, cy + hr, cz + EPS);
            case SOUTH -> new AABB(cx - hr, cy - hr, cz - EPS, cx + hr, cy + hr, cz + EPS);
            case WEST  -> new AABB(cx - EPS, cy - hr, cz - hr, cx + EPS, cy + hr, cz + hr);
            case EAST  -> new AABB(cx - EPS, cy - hr, cz - hr, cx + EPS, cy + hr, cz + hr);
            default    -> new AABB(cx - hr, cy - hr, cz - hr, cx + hr, cy + hr, cz + hr);
        };
    }

    @Override
    public boolean survives() {
        if (this.level() == null) return true;
        Direction dir = getDirection();
        BlockPos anchorPos = this.blockPosition().relative(dir.getOpposite());
        return this.level().getBlockState(anchorPos)
                   .isFaceSturdy(this.level(), anchorPos, dir, SupportType.RIGID)
               || this.level().getBlockState(anchorPos)
                   .isFaceSturdy(this.level(), anchorPos, dir, SupportType.FULL);
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.ITEM_FRAME_PLACE, 1.0f, 1.0f);
    }

    @Override
    public void dropItem(net.minecraft.world.entity.Entity breaker) {
        ItemStack drop = new ItemStack(ModItems.PRINTED_PHOTO.get());
        String fn = getFilename();
        if (!fn.isEmpty()) {
            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
            nbt.putString("photoFilename", fn);
            drop.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(nbt));
        }
        spawnAtLocation(drop);
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack stack = new ItemStack(ModItems.PRINTED_PHOTO.get());
        String fn = getFilename();
        if (!fn.isEmpty()) {
            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
            nbt.putString("photoFilename", fn);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(nbt));
        }
        return stack;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("photoFilename", getFilename());
        tag.putByte("Facing", (byte) getDirection().get3DDataValue());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("photoFilename"))
            entityData.set(FILENAME, tag.getString("photoFilename"));
        if (tag.contains("Facing"))
            setDirection(Direction.from3DDataValue(tag.getByte("Facing")));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity,
                getDirection().get3DDataValue());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDirection(Direction.from3DDataValue((int) packet.getData()));
    }
}
