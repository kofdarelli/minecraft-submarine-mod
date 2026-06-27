package com.submarine.template;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SeatSpec(int index, BlockPos localPos, Direction facing) {
}
