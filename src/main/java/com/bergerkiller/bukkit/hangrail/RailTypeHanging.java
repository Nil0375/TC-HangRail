package com.bergerkiller.bukkit.hangrail;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeHorizontal;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeRegular;

public class RailTypeHanging extends RailTypeHorizontal {
    private final ItemParser typeInfo;
    private final int offset;
    private final int signOffset;
    private final BlockFace signDirection;
    private final RailLogicHangingSloped[] logic_sloped;
    private final RailLogicHanging[] logic_horizontal;

    public RailTypeHanging(ItemParser typeInfo, int offset, int signOffset, BlockFace signDirection) {
        this.typeInfo = typeInfo;
        this.offset = offset;
        this.signOffset = signOffset;
        if (signDirection == BlockFace.SELF) {
            this.signDirection = this.isBelowRail() ? BlockFace.UP : BlockFace.DOWN;
        } else {
            this.signDirection = signDirection;
        }

        this.logic_sloped = new RailLogicHangingSloped[4];
        for (int i = 0; i < 4; i++) {
            this.logic_sloped[i] = new RailLogicHangingSloped(FaceUtil.notchToFace(i << 1), this);
        }

        this.logic_horizontal = new RailLogicHanging[8];
        for (int i = 0; i < 8; i++) {
            this.logic_horizontal[i] = new RailLogicHanging(FaceUtil.notchToFace(i), this);
        }
    }

    /**
     * Gets whether the below-rail logic should be executed (offset < 0)
     * 
     * @return True if minecart is below rail
     */
    public boolean isBelowRail() {
        return this.offset < 0;
    }

    /**
     * Gets the block offset above/below this rail the Minecart is positioned
     * 
     * @return offset
     */
    public int getOffset() {
        return this.offset;
    }

    /**
     * Gets the hanging rail logic to go into the direction specified
     * 
     * @param direction to go to
     * @return Horizontal rail logic for that direction
     */
    public RailLogicHanging getLogicHorizontal(BlockFace direction) {
        return this.logic_horizontal[FaceUtil.faceToNotch(direction)];
    }

    /**
     * Gets the sloped hanging rail logic to go into the direction specified
     * 
     * @param direction to go to
     * @return Sloped hanging rail logic for that direction
     */
    public RailLogicHangingSloped getLogicSloped(BlockFace direction) {
        return this.logic_sloped[FaceUtil.faceToNotch(direction) >> 1];
    }

    @Override
    public boolean isRail(BlockData blockData) {
        return typeInfo.match(blockData);
    }

    @Override
    public Block findRail(Block pos) {
        if (this.isBelowRail()) {
            // Minecart is below the rails, then the block most down has priority
            if (isRail(pos.getWorld(), pos.getX(), pos.getY() - this.offset, pos.getZ())) {
                return pos.getRelative(0, -this.offset, 0);
            }

            // Now check the rail one higher (slopes)
            if (isRail(pos.getWorld(), pos.getX(), pos.getY() - this.offset + 1, pos.getZ())) {
                Block rail = pos.getRelative(0, -this.offset + 1, 0);
                if (findSlope(rail) != null) {
                    return rail;
                }
            }
        } else {
            // Now check the rails at the offset position (horizontal)
            if (isRail(pos.getWorld(), pos.getX(), pos.getY() - this.offset, pos.getZ())) {
                Block rail = pos.getRelative(0, -this.offset, 0);
                if (!isRail(rail, BlockFace.UP)) {
                    return rail;
                }
            }

            // Minecart is above the rails, then the block most up has priority (slope)
            if (isRail(pos.getWorld(), pos.getX(), pos.getY() - this.offset - 1, pos.getZ())) {
                return pos.getRelative(0, -this.offset - 1, 0);
            }
        }
        return null;
    }

    @Override
    public Block findMinecartPos(Block trackBlock) {
        return trackBlock.getRelative(0, this.offset, 0);
    }

    @Override
    public BlockFace[] getPossibleDirections(Block trackBlock) {
        return RailTypeRegular.getPossibleDirections(getDirection(trackBlock));
    }

    @Override
    public BlockFace getSignColumnDirection(Block railsBlock) {
        return this.signDirection;
    }

    @Override
    public Block getSignColumnStart(Block railsBlock) {
        if (this.signOffset == 0) {
            return railsBlock;
        } else {
            return railsBlock.getRelative(0, this.signOffset, 0);
        }
    }

    @Override
    public BlockFace getDirection(Block railsBlock) {
        BlockFace sloped = findSlope(railsBlock);
        return sloped == null ? getHorizontalDirection(railsBlock) : sloped;
    }

    @Override
    public RailLogic getLogic(MinecartMember<?> member, Block railsBlock, BlockFace direction) {
        BlockFace sloped = findSlope(railsBlock);
        if (sloped != null) {
            return this.getLogicSloped(sloped);
        }
        // Check what sides have a connecting hanging rail
        BlockFace dir = getHorizontalDirection(railsBlock);
        if (dir == BlockFace.SELF) {
            // Use the Minecart direction to figure this one out
            // This is similar to the Crossing rail type
            dir = FaceUtil.toRailsDirection(direction);
        }
        return this.getLogicHorizontal(dir);
    }

    private BlockFace findSlope(Block railsBlock) {
        if (this.isBelowRail()) {
            // Minecart is below the rails ('hang rail')
            // Do sloped logic specific to that, here
            Block below = railsBlock.getRelative(BlockFace.DOWN);
            for (BlockFace face : FaceUtil.AXIS) {
                if (isRail(below, face)) {
                    // Check that the block below is not blocked (pillar)
                    if (!isRail(below.getRelative(face.getModX(), -1, face.getModZ()))) {
                        return face.getOppositeFace();
                    }
                }
            }
        } else {
            // Minecart is above the rails ('floating rail')
            // Do sloped logic specific to that, here.
            Block above = railsBlock.getRelative(BlockFace.UP);
            for (BlockFace face : FaceUtil.AXIS) {
                if (isRail(above, face)) {
                    // Check that the block above is not blocked (pillar)
                    if (!isRail(above.getRelative(face.getModX(), 1, face.getModZ()))) {
                        return face;
                    }
                }
            }
        }
        return null;
    }

    private BlockFace getHorizontalDirection(Block railsBlock) {
        boolean north = isRail(railsBlock, BlockFace.NORTH);
        boolean east = isRail(railsBlock, BlockFace.EAST);
        boolean south = isRail(railsBlock, BlockFace.SOUTH);
        boolean west = isRail(railsBlock, BlockFace.WEST);
        // X-crossing: use direction we came from
        if (north && south && east && west) {
            return BlockFace.SELF;
        }
        // NORTH and SOUTH only
        if (north && south) {
            return BlockFace.NORTH;
        }
        // EAST and WEST only
        if (east && west) {
            return BlockFace.EAST;
        }
        // Along NORTH-EAST
        if (north && east) {
            return BlockFace.SOUTH_WEST;
        }
        // Along NORTH-WEST
        if (north && west) {
            return BlockFace.SOUTH_EAST;
        }
        // Along SOUTH-EAST
        if (south && east) {
            return BlockFace.NORTH_WEST;
        }
        // Along SOUTH-WEST
        if (south && west) {
            return BlockFace.NORTH_EAST;
        }
        // See if there is one possible neighbor
        if (north || south) {
            return BlockFace.NORTH;
        }
        if (east || west) {
            return BlockFace.EAST;
        }
        // No neighbors at all - stick to the direction we came from
        return BlockFace.SELF;
    }
}
