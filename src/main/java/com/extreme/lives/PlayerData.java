package com.extreme.lives;

import java.util.UUID;

/** Datos persistentes de un jugador (se identifica por UUID, el nombre es solo informativo). */
public final class PlayerData {

    public final UUID id;
    public String name;
    public int lives;
    /** Momento de la última muerte contada (anti doble resta). No se guarda en disco. */
    public transient long lastDeath;

    public PlayerData(UUID id, String name, int lives) {
        this.id = id;
        this.name = name;
        this.lives = lives;
    }
}
