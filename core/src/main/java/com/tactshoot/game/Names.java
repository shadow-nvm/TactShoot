package com.tactshoot.game;

import com.badlogic.gdx.math.MathUtils;

/** Random soldier names for bots and default players. */
public final class Names {

    private static final String[] PARTS_A = {
            "Ghost", "Viper", "Havoc", "Blaze", "Wraith", "Titan", "Nova", "Rex",
            "Falcon", "Onyx", "Storm", "Raptor", "Cobra", "Zero", "Jager", "Kilo",
            "Echo", "Marauder", "Shadow", "Ironside", "Phoenix", "Raven", "Sabre", "Volt"
    };

    private static final String[] PARTS_B = {
            "One", "X", "Prime", "Wolf", "Fang", "Strike", "Fox", "Two",
            "Rex", "Six", "Owl", "Bear", "Dagger", "Rush", "Ace", "Ninja"
    };

    private Names() {
    }

    public static String random() {
        if (MathUtils.randomBoolean()) {
            return PARTS_A[MathUtils.random(PARTS_A.length - 1)]
                    + MathUtils.random(10, 99);
        }
        return PARTS_A[MathUtils.random(PARTS_A.length - 1)] + " "
                + PARTS_B[MathUtils.random(PARTS_B.length - 1)];
    }
}
