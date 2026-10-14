package com.extreme.difficulty;

/** Valores de dificultad de un día concreto. */
public record DifficultyProfile(
        int day,
        double damage,
        double health,
        double speed,
        double followRange,
        double cobwebChance,          // -1 = usar el valor base de mobs.yml
        double effectChanceBonus,
        double effectDurationMultiplier,
        double buffChance,
        int buffAmplifier,
        String label) {

    public static final DifficultyProfile IDENTITY =
            new DifficultyProfile(1, 1.0, 1.0, 1.0, 1.0, -1, 0, 1.0, 0, 0, "Normal");

    public DifficultyProfile withDay(int newDay) {
        return new DifficultyProfile(newDay, damage, health, speed, followRange, cobwebChance,
                effectChanceBonus, effectDurationMultiplier, buffChance, buffAmplifier, label);
    }
}
