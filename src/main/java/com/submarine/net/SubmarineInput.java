package com.submarine.net;

import java.util.UUID;

public record SubmarineInput(float forward, float turn, float vertical, UUID pilot, long tick) {
    public boolean isZero() {
        return forward == 0.0F && turn == 0.0F && vertical == 0.0F;
    }
}
