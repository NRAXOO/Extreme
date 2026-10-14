package com.extreme.pvp;

/** Tarea de un solo uso: alterna el PVP y programa el siguiente cambio aleatorio. */
public class PVPRandomTask implements Runnable {

    private final PVPManager manager;

    public PVPRandomTask(PVPManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        manager.fireRandom();
    }
}
