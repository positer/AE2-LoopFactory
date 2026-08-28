package com.example.ae2lightoptimizer.solver;

public record RecipeApplication(String recipeId, long times) {
    public RecipeApplication {
        if (recipeId == null || recipeId.isBlank() || times <= 0) {
            throw new IllegalArgumentException("Recipe application must be named and positive");
        }
    }
}
