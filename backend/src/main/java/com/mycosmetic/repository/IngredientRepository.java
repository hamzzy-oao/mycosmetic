package com.mycosmetic.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mycosmetic.entity.Ingredient;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

	Optional<Ingredient> findByStandardName(String standardName);
}
